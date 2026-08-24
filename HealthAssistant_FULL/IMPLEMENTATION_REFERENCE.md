# Implementation Reference — Quick Guide

## Chat Persistence Flow

### Data Layer
```
PatientProfile (id, name, age, ...)
    ↓ (FK: patientId)
ChatMessage (id, patientId, text, isUser, urgency, timestamp)
```

### Room Setup
```kotlin
// 1. Entity (data model)
@Entity(tableName = "chat_message", foreignKeys = [...])
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: Long,
    val text: String,
    val isUser: Boolean,
    val urgency: String? = null,
    val createdAtEpochMillis: Long = System.currentTimeMillis()
)

// 2. DAO (database access)
@Dao
interface ChatMessageDao {
    @Insert
    suspend fun insert(message: ChatMessage): Long

    @Query("SELECT * FROM chat_message WHERE patientId = :patientId ORDER BY createdAtEpochMillis ASC")
    fun observeForPatient(patientId: Long): Flow<List<ChatMessage>>
}

// 3. Database (add entity + migration)
@Database(entities = [..., ChatMessage::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatMessageDao(): ChatMessageDao
    
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS chat_message (...)
            """)
        }
    }
}
```

### ViewModel Setup
```kotlin
// Before: Mutable state (lost on restart)
// private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())

// After: Database-backed flow (persisted)
val messages: StateFlow<List<ChatMessage>> = _patientId
    .flatMapLatest { id ->
        if (id < 0) MutableStateFlow(emptyList()) 
        else db.chatMessageDao().observeForPatient(id)  // ← Auto-updates
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

// Send message: save to DB
fun sendMessage(text: String) {
    val patientId = _patientId.value
    viewModelScope.launch {
        // 1. Save user message
        db.chatMessageDao().insert(
            ChatMessage(
                patientId = patientId,
                text = text,
                isUser = true,
                urgency = UrgencyClassifier.classify(text)
            )
        )
        
        // 2. Generate reply
        val reply = gemma.generateReply(text)
        
        // 3. Save AI reply
        db.chatMessageDao().insert(
            ChatMessage(
                patientId = patientId,
                text = reply,
                isUser = false
            )
        )
        // UI auto-updates via messages Flow ↑
    }
}
```

### UI (No Changes Needed)
```kotlin
@Composable
fun ChatScreen(
    messages: List<ChatMessage>,  // ← Still receives the same list
    onSend: (String) -> Unit
) {
    LazyColumn {
        items(messages) { msg ->
            ChatBubble(msg)  // Uses msg.text, msg.isUser, msg.urgency
        }
    }
}
```

**Key Insight:** UI doesn't know where messages come from (Flow vs MutableStateFlow). 
The switch from volatile state → persistent DB is invisible to the UI. ✨

---

## Model URL Configuration

### Current Setup (Working)
```kotlin
private val gemmaModelUrl = 
    "https://storage.googleapis.com/mediapipe-tasks/documents/models/llm_text_generator/gemma-2b-it-cpu-int4.task"
```

### Before Shipping
1. **Test the URL:**
   ```bash
   curl -I "https://storage.googleapis.com/mediapipe-tasks/.../gemma-2b-it-cpu-int4.task"
   # Should get 200 OK
   ```

2. **If URL fails later, use alternatives:**

   **Option A: Firebase Storage**
   ```
   1. Create Firebase project
   2. Upload .task file to Cloud Storage
   3. Set public read access
   4. Use generated URL: https://firebasestorage.googleapis.com/v0/b/...
   ```

   **Option B: Cloudflare R2**
   ```
   1. Create R2 bucket
   2. Upload model
   3. Make public
   4. Use: https://yourbucket.your-r2-domain.com/gemma-2b-it-cpu-int4.task
   ```

   **Option C: Self-Hosted**
   ```
   nginx.conf:
   server {
       listen 80;
       location /models/ {
           alias /var/www/models/;
       }
   }
   ```

3. **Add integrity check (optional but recommended):**
   ```kotlin
   // Calculate once and hardcode
   private const val GEMMA_MODEL_SHA256 = "abc123..."
   
   // Verify after download
   fun verifyModelIntegrity(file: File): Boolean {
       val hash = file.sha256Hex()
       return hash == GEMMA_MODEL_SHA256
   }
   ```

---

## Testing Checklist

### Unit/Instrumented Tests
```kotlin
// Test: Chat message persists after app restart
@Test
fun testChatPersistence() = runTest {
    val patientId = db.patientProfileDao()
        .insert(PatientProfile(age = 35, ...))
    
    // Insert message
    db.chatMessageDao().insert(
        ChatMessage(patientId = patientId, text = "আমার মাথা ব্যথা", isUser = true)
    )
    
    // Verify it's in DB
    val messages = db.chatMessageDao()
        .getRecentForPatient(patientId, limit = 10)
    assert(messages.size == 1)
    assert(messages[0].text == "আমার মাথা ব্যথা")
}

// Test: Model URL is reachable
@Test
suspend fun testModelUrlAvailable() {
    val response = httpClient.head(GEMMA_MODEL_URL)
    assertEquals(200, response.status.value)
}
```

### Manual Tests
- [ ] Fresh install → onboard → send chat message → restart app → chat history visible
- [ ] Chat message shows correct urgency classification
- [ ] Large chat history (1000+ messages) loads without lag
- [ ] Model download with interrupted WiFi → resume works
- [ ] Budget phone (2GB RAM) → model loads (or graceful fallback)

---

## Database Migration Explanation

### Why We Needed It
- **Before:** App v1 had no chat history table
- **Update:** Added chat history feature
- **Room requires:** Explicit migration from v1 → v2

### Migration Code
```kotlin
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create new table (same SQL as entity definition)
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS chat_message (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                patientId INTEGER NOT NULL,
                text TEXT NOT NULL,
                isUser INTEGER NOT NULL,
                urgency TEXT,
                createdAtEpochMillis INTEGER NOT NULL,
                FOREIGN KEY(patientId) REFERENCES patient_profile(id) ON DELETE CASCADE
            )
        """)
        
        // Create index for fast queries
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS idx_chat_message_patientId 
            ON chat_message(patientId)
        """)
    }
}
```

### Future Migrations (v2 → v3, etc.)
```kotlin
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Example: add new column to chat_message
        database.execSQL("ALTER TABLE chat_message ADD COLUMN aiModel TEXT DEFAULT 'gemma-2b'")
    }
}

// Register in Room builder
.addMigrations(MIGRATION_1_2, MIGRATION_2_3)
```

---

## Common Issues & Fixes

### Issue: "Cannot create an instance of class ChatMessage"
**Cause:** ChatMessage imported from wrong package (old UI class vs new entity)
**Fix:** 
```kotlin
// Wrong
import com.srgroup.healthassistant.ui.chat.ChatMessage

// Right
import com.srgroup.healthassistant.data.model.ChatMessage
```

### Issue: "Migration from 1 to 2 is not available"
**Cause:** MIGRATION_1_2 not registered in .addMigrations()
**Fix:**
```kotlin
Room.databaseBuilder(...)
    .addMigrations(MIGRATION_1_2)  // ← Add this
    .build()
```

### Issue: Model download gets stuck at 0%
**Cause:** Gemma model URL unreachable or slow network
**Fix:**
1. Check URL is still public/available
2. Use Model URL tester: `curl -I <URL>`
3. Monitor user network (Bangladesh often has slow/unstable connections)
4. Consider resumable downloads

### Issue: "OOM / App crashes after model loads"
**Cause:** Device has <2GB free RAM
**Fix:** 
1. Fallback to rules-based urgency classifier (skip Gemma)
2. Use Gemma 1B instead of 2B
3. Add warning: "Requires 2GB+ free memory"

---

## Performance Tips

### Chat Rendering (1000+ messages)
```kotlin
// Bad: All messages at once
LazyColumn {
    items(allMessages) { ChatBubble(it) }
}

// Good: Pagination
LazyColumn {
    items(recentMessages.take(50)) { ChatBubble(it) }
    if (recentMessages.size > 50) {
        item { LoadMoreButton() }
    }
}
```

### Database Queries
```kotlin
// Bad: Load all messages every time
db.chatMessageDao().getRecentForPatient(patientId, limit = 99999)

// Good: Limit and use Flow for updates
db.chatMessageDao().getRecentForPatient(patientId, limit = 100)
db.chatMessageDao().observeForPatient(patientId)  // for reactive UI
```

---

## Schema Export (for future migrations)

Room exports schema to `app/schemas/` folder (JSON format):
```
app/schemas/
├── com.srgroup.healthassistant.data.db.AppDatabase/
│   ├── 1.json   (version 1 schema)
│   ├── 2.json   (version 2 schema with chat_message)
│   └── 3.json   (future versions)
```

Use these files to:
1. Document schema evolution
2. Test migrations without running the app
3. Review what changed between versions

Enable in `build.gradle.kts`:
```kotlin
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
```
