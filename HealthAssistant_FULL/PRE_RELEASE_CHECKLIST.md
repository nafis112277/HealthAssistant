# Pre-Release Checklist (ফাইনাল রিলিজের আগে)

## Category 1: Model & Performance

### Gemma Model Setup
- [ ] **URL Verification**: Test model URL is still accessible
  ```bash
  curl -I "https://storage.googleapis.com/mediapipe-tasks/documents/models/llm_text_generator/gemma-2b-it-cpu-int4.task"
  # Should return 200 OK, Content-Length: ~2.5GB
  ```
  
- [ ] **Integrity Check**: Calculate and store model file hash
  ```bash
  # Download once, get MD5/SHA256
  md5sum gemma-2b-it-cpu-int4.task
  sha256sum gemma-2b-it-cpu-int4.task
  
  # Add to app (optional but recommended for budget phones with corruption issues)
  private const val GEMMA_MODEL_SHA256 = "<hash>"
  ```

- [ ] **Alternative Hosting Ready**: If Google URL fails later
  - [ ] Firebase Storage account setup
  - [ ] Cloudflare R2 account + bucket created
  - [ ] Or self-hosted server configured

### Real Device Testing (Critical)
- [ ] **Budget Phone Test** (বাজেট ফোন — Samsung M-series, Xiaomi Redmi, Realme)
  - [ ] Model download completes without timeout
  - [ ] Model loads (check logcat for OOM crashes)
  - [ ] Chat reply generation doesn't freeze/ANR
  - [ ] Chat history persists after restart
  
- [ ] **High-End Phone Test** (Flagship — Samsung S24, iPhone equivalency)
  - [ ] Chat snappy and responsive
  - [ ] Multiple images/files in health records upload smoothly
  
- [ ] **Network Conditions**
  - [ ] WiFi: Download completes fast
  - [ ] 4G: Download slower but doesn't timeout
  - [ ] 3G/2G: Download pauses, verify user doesn't force-close
  - [ ] Airplane mode: Graceful error handling

---

## Category 2: Database & Data Integrity

### Chat Persistence
- [ ] **Test migration v1→v2 automatically**
  ```
  1. Install v1 APK (before chat feature)
  2. Create patient profile + add some data
  3. Update to v2 APK
  4. Verify chat_message table created, no crashes
  ```
  
- [ ] **Chat History Survives Restart**
  ```
  1. Send 5 messages in chat
  2. Kill app (pull battery / adb kill)
  3. Relaunch app
  4. Verify all 5 messages still there, chronological
  ```

- [ ] **Large Chat History**
  - [ ] Send 500+ messages
  - [ ] Check UI still responsive (no lag)
  - [ ] Verify all messages loadable (pagination works if implemented)

- [ ] **Data Deletion Safety**
  - [ ] Delete patient profile
  - [ ] Verify chat_message records cascade-delete (foreign key working)

### Medication Reminder Logic
- [ ] Adherence calculation correct (7-day window logic)
- [ ] Reminders trigger at correct times (AlarmManager)
- [ ] "Took medication" button actually logs adherence

### Health Records
- [ ] File picker works (image, PDF, documents)
- [ ] Files stored in app-private storage (no leaks)
- [ ] Files survive backup/restore cycles

---

## Category 3: AI Safety & Compliance

### Urgency Classifier
- [ ] High-risk keywords detected correctly
  - [ ] "বুকে ব্যথা" → High
  - [ ] "শ্বাসকষ্ট" → High
  - [ ] "জ্বর" → Medium
  - [ ] "সাধারণ প্রশ্ন" → Low
  
- [ ] Gemma system prompt enforced
  - [ ] Never diagnoses ("রোগ নির্ণয় করে না")
  - [ ] Never prescribes ("ওষুধ সুপারিশ করে না")
  - [ ] Always refers to doctor
  - [ ] High-risk → immediate emergency referral

### Bangla Language
- [ ] Onboarding form labels readable in Bengali
- [ ] Chat AI reply in Bangla (not mixed Bengali/English gibberish)
- [ ] Error messages in Bangla
- [ ] Doctor PIN entry supports Bangla numerals (if needed)

### Liability/Disclaimers
- [ ] "এই সহায়ক ডাক্তার নয়" disclaimer visible on chat screen
- [ ] Red banner on chat reminding: "জরুরি উপসর্গে সরাসরি হাসপাতালে যান"
- [ ] Terms of Service mention: "শুধুমাত্র তথ্যের জন্য, চিকিৎসা পরামর্শ নয়"

---

## Category 4: Security & Auth

### Local Auth (Current)
- [ ] Admin password hashing works (SHA-256, basic protection acknowledged)
- [ ] Doctor PIN stored safely (not plain text)
- [ ] Onboarding doesn't leak patient data

### Before Production: Implement Server Auth
- [ ] Doctor backend auth (username + password / OAuth)
- [ ] Admin backend auth
- [ ] Credential transmission encrypted (HTTPS only)
- [ ] Session tokens + expiry

---

## Category 5: Build & Deployment

### APK Build
- [ ] `./gradlew clean build` succeeds without warnings
- [ ] APK size reasonable (~50-100 MB expected)
- [ ] ProGuard/R8 minification enabled for release build

- [ ] Sign APK with release keystore
  ```bash
  jarsigner -verbose -sigalg SHA1withRSA -digestalg SHA1 \
    -keystore my-release-key.jks \
    app-release-unsigned.apk my-key-alias
  ```

### Version & Build Number
- [ ] `versionName = "1.0.0"` (semantic versioning)
- [ ] `versionCode` incremented
- [ ] README.md updated with release notes

### Permissions Check
- [ ] INTERNET — for model download ✅
- [ ] SCHEDULE_EXACT_ALARM — for medication reminder ✅
- [ ] POST_NOTIFICATIONS — for reminder notifications ✅
- [ ] READ_EXTERNAL_STORAGE / WRITE_EXTERNAL_STORAGE (if needed)

---

## Category 6: User Onboarding & Docs

### In-App Help
- [ ] First-time user tutorial clear
- [ ] "How to use chat" instructions in Bangla
- [ ] Medication reminder setup walkthrough

### User-Facing Docs
- [ ] README.md for users (in Bangla, if possible)
- [ ] FAQ: "Why model download is slow?" / "Why chat sometimes doesn't respond?"
- [ ] Contact email for bugs/feedback

### Admin/Doctor Docs
- [ ] How to log in as doctor (PIN setup)
- [ ] How to view patient dashboard
- [ ] How to approve follow-up drafts

---

## Category 7: Monitoring & Analytics

### Logging (for debugging)
- [ ] Model download progress logged
- [ ] Chat errors logged (Gemma failures)
- [ ] Database migration logged
- [ ] Medication reminder triggers logged

### Crash Reporting
- [ ] Firebase Crashlytics integrated (optional but recommended)
- [ ] Uncaught exceptions caught gracefully
- [ ] OOM crashes logged (not just silent failure)

### User Feedback
- [ ] In-app feedback button / email collection
- [ ] Error messages help user report issue

---

## Category 8: Legal & Compliance

### Data Privacy
- [ ] Patient data stored locally (no cloud sync without consent)
- [ ] No analytics tracking without opt-in (respect privacy, esp. medical data)
- [ ] Privacy policy available (link in app)

### Medical Disclaimers
- [ ] "Not a doctor" disclaimer prominent
- [ ] "For educational purposes only" on chat
- [ ] Emergency contact on high-risk alerts

### Terms of Service
- [ ] Liability limited (AI mistakes not our fault)
- [ ] User responsible for seeking real medical care
- [ ] Data will be deleted on request (GDPR-like compliance)

---

## Category 9: Known Limitations (Communicate to Users)

- [ ] **RAM requirement:** "App requires 2GB+ free memory for AI chat"
- [ ] **Offline:** "First download of AI model required; ~2.5GB"
- [ ] **Bangladesh-specific:** "Optimized for Bangla language; English chat may be lower quality"
- [ ] **Not a doctor:** "This is an information tool, not a substitute for medical advice"

---

## Category 10: Final Sign-Off

Before uploading to Play Store / distribution:

- [ ] **Code Review**
  - [ ] No hardcoded passwords/API keys
  - [ ] No TODOs left in code
  - [ ] Imports clean (no unused imports)
  
- [ ] **Documentation**
  - [ ] Model setup guide finalized
  - [ ] Migration instructions clear
  - [ ] Testing results documented
  
- [ ] **Testing Summary**
  - [ ] Date tested: ___________
  - [ ] Devices tested: ___________
  - [ ] Known issues: ___________
  - [ ] Pass/Fail: ___________
  
- [ ] **Sign-Off**
  - [ ] Developer: ___________ (signature)
  - [ ] Date: ___________
  - [ ] Version: ___________

---

## Post-Release Monitoring (First Month)

After launch, monitor:
- [ ] Model download success rate (track failures)
- [ ] Chat error frequency (Gemma crashes)
- [ ] App crash rate (OOM on budget phones)
- [ ] User feedback (health/safety issues)
- [ ] Model URL accessibility (test weekly)

If issues found, prioritize:
1. **Safety issues** (wrong urgency classification) → Hotfix immediately
2. **Model crashes** (OOM) → Implement fallback or smaller model
3. **Download failures** → Switch model URL or provide manual setup option

---

## Future Improvements (Post v1.0)

- [ ] Backend server auth (doctor/admin)
- [ ] Cloud backup of chat history (user consent)
- [ ] Lightweight fallback (Gemma 1B or rules-only)
- [ ] Offline chat using local Ollama server
- [ ] Multi-language support (English, Hindi, Urdu)
- [ ] Doctor-to-patient messaging
- [ ] Integration with EHR systems (hospital records)
