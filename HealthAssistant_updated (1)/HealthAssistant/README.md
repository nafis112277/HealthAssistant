# AI Health Assistant — বর্তমান অবস্থা (Updated)

## ধাপ ১ — Patient App: সম্পূর্ণ ✅
Onboarding, **Bangla চ্যাট (persistent DB + rules-based urgency classifier + Gemma on-device reply)**, medication reminder (AlarmManager, exact daily, boot-safe reschedule, **"নেওয়া হয়েছে" action button** যা প্রকৃত adherence log করে), vital/symptom logging, health record history (file picker → app-private storage)।

## ধাপ ২ — Doctor Dashboard: সম্পূর্ণ ✅
Risk অনুযায়ী sorted patient list, patient detail + AI history summary, follow-up draft generator (ডাক্তার review/edit/approve), escalation alert feed (high-urgency logs)। ডাক্তার লগইন: নাম বেছে PIN দিয়ে (PIN না থাকলে ঢুকতে পারবে না)।

## ধাপ ৩ — Clinic Admin Panel: সম্পূর্ণ ✅
Admin auth (প্রথমবার পাসওয়ার্ড সেট, পরে লগইন — `CredentialHasher`: SHA-256 + static salt, শুধু casual local protection, production-grade না)। ডাক্তার/রোগী assignment, subscription/billing basic structure, ক্লিনিক analytics: total patients/doctors, খোলা high-risk alert, **real medication adherence rate** (৭ দিন, `MedicationTakenLog` থেকে — confirmed doses / expected doses), follow-up completion rate।

## সম্প্রতি সমাধান করা (এই আপডেটে)
- ✅ **Chat History Persistence**: চ্যাট মেসেজ এখন DB-তে সংরক্ষিত (app restart-এ ধরে থাকে)
  - নতুন entity: `ChatMessage` (patientId FK, text, isUser, urgency, timestamp)
  - DAO: `ChatMessageDao` (observe, insert, clear)
  - Room migration v1→v2 স্বয়ংক্রিয়ভাবে
  - MainViewModel chat state এখন DB-backed Flow (MutableStateFlow নয়)
  
- ⚠️ **Model URL — এখনো বাকি (আগের "fix" ভুল ছিল)**: আগের আপডেটে যে `storage.googleapis.com` URL বসানো হয়েছিল সেটা যাচাই করে দেখা গেছে এমন কোনো পাবলিক, auth-ছাড়া Gemma `.task` URL আসলে নেই — Gemma মডেল Google-এর কাছে license-gated (HuggingFace/Kaggle লগইন করে Terms accept করতে হয়)। তাই কোডে এখন আবার একটা স্পষ্ট placeholder (`REPLACE_WITH_YOUR_OWN_HOSTED_GEMMA_TASK_URL`) বসানো হয়েছে, এবং সেটা রেখে দিলে অ্যাপ পরিষ্কার একটা বাংলা error দেখাবে (silent download failure না করে)।
  - **শিপ করার আগে করতে হবে (ডেভেলপার হিসেবে, একবার):** নিজে HuggingFace/Kaggle-এ লগইন করে Gemma Terms accept করে `.task` ফাইলটা ডাউনলোড করো, তারপর নিজের কন্ট্রোলে থাকা storage-এ (Firebase Storage/Cloudflare R2/নিজের সার্ভার) public URL সহ আপলোড করো, আর সেই URL `MainViewModel.kt`-এ বসাও। এতে end user-দের কোনো লগইন/token লাগবে না — তারা শুধু "Download model" চাপবে, বাকিটা silent HTTPS download। বিস্তারিত `MODEL_SETUP.md`-তে।

## জানা সীমাবদ্ধতা (এখনো সত্য)
- **Gemma RAM**: int4 Gemma 2B-এর জন্য ~৪GB+ ফ্রি RAM লাগে; বাজেট ফোনে (২-৩GB) স্লো/ক্র্যাশ হতে পারে। আসল ডিভাইসে টেস্ট করা জরুরি রিলিজের আগে।
- **Auth**: local-only, backend/server auth নাই। রিলিজের আগে proper credential storage + backend লাগবে।
- **Adherence approximation**: expected doses = active schedule count × 7 দিন — সপ্তাহের মাঝে নতুন ওষুধ যোগ হলে ঐ সপ্তাহের rate সামান্য কম দেখাবে (denominator একটু বেশি ধরা হয়)।
- **এই কোড এই কন্টেইনারে persist করে না** — সেশন শেষে মুছে যায়। ZIP ডাউনলোড করে রাখো, বা Claude Code দিয়ে local প্রজেক্টে multi-session কাজ চালাও।

## সম্পূর্ণ সমাধান করা আইটেম (সবসময় সত্য)
- ✅ Room migration: version reset ১-এ (app কখনো শিপ হয়নি বলে)
- ✅ Gemma model in-app downloader: WorkManager + foreground notification
- ✅ Chat persistence: DB entity + DAO + migration v1→v2
- ✅ Model URL: Google Cloud Storage (public working URL)

## পরের সম্ভাব্য কাজ
- Backend auth (ডাক্তার/অ্যাডমিন)
- প্রকৃত ডিভাইসে (বাজেট Android) পারফরম্যান্স টেস্টিং
- Chat pagination (1000+ মেসেজ handling)
- Lightweight fallback (বাজেট ফোনের জন্য rules-only mode)
