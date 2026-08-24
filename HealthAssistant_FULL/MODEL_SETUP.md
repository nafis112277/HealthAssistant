# Gemma Model Setup Guide

## Current Status — ACTION REQUIRED
- The app ships with `gemmaModelUrl = "REPLACE_WITH_YOUR_OWN_HOSTED_GEMMA_TASK_URL"` — this is a
  placeholder, not a working link. **There is no anonymous public URL for Gemma .task files.**
  A previous pass of this project had set the URL to a `storage.googleapis.com` link claiming it
  was "public, no auth required" — that URL could not be verified to exist and should be treated
  as incorrect. Do not restore it.
- Gemma model weights are gated by Google: getting the file requires logging in to Hugging Face
  or Kaggle and accepting the Gemma Terms of Use (https://ai.google.dev/gemma/terms) as the
  *developer*. There is no way for an app to fetch them anonymously at request time.
- Size: ~2.5 GB (int4 quantized)
- Format: MediaPipe .task format (LiteRT-compatible)

## Before Shipping (mandatory, not optional)

### 1. Get the model file once, as the developer
- Log in to Hugging Face (https://huggingface.co/google/gemma-2b-it) or Kaggle
  (https://www.kaggle.com/models/google/gemma-2), accept the Gemma Terms of Use, and download
  the converted `.task` file (see https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference
  for the conversion docs — Google's own "AI Edge Gallery" sample app uses this same gated flow).
- This is a one-time step you do yourself; end users of the shipped app never see a login prompt.

### 2. Re-host it on storage YOU control
Upload the downloaded `.task` file to a bucket/CDN you own (Firebase Storage, Cloudflare R2,
S3, your own server) with public read access, then put that URL into `gemmaModelUrl` in
`MainViewModel.kt`. This keeps the in-app download frictionless for end users (silent HTTPS GET,
no auth) while keeping you in control of uptime — unlike depending on a third-party mirror,
which can be taken down without notice and would break the app for every installed user at once.

### 3. Verify Model Integrity
- Download the file once and calculate its MD5/SHA256
- Store this hash in a configuration file or hardcode it in the app
- On download, verify the downloaded file matches the expected hash
- This protects against corrupted downloads breaking the app

### 4. Where to Host It (pick one)

#### Option A: Firebase Storage (Recommended for Bangladesh users)
```
1. Create Firebase project at firebase.google.com
2. Upload .task file to Cloud Storage (free tier: 5GB/month)
3. Set read permissions to public
4. Use the public URL in MainViewModel.gemmaModelUrl
```

#### Option B: Cloudflare R2
```
1. Sign up at cloudflare.com
2. Create R2 bucket
3. Upload model file
4. Enable public access
5. Use R2 public URL
```

#### Option C: Self-Host
```
1. Set up a basic web server (nginx/Apache)
2. Serve the .task file
3. Configure gzip/brotli for faster downloads
4. Consider CDN (CloudFlare, Akamai) for global distribution
```

### 5. App Configuration Updates

Update `MainViewModel.kt` when changing the URL:
```kotlin
private val gemmaModelUrl = "YOUR_ACTUAL_URL"
```

Then rebuild and test:
```bash
./gradlew clean build
```

## Troubleshooting

### Download Fails with 404
- Check the URL is still valid and public
- Try downloading manually in a browser

### Download Takes Too Long
- Users may be on slow connections (common in Bangladesh)
- Consider: (a) resumable downloads, (b) smaller quantized version, (c) progressive model loading

### Model loads but runs slowly / OOM crash
- Gemma 2B (int4) needs ~4GB free RAM
- On budget phones (<2GB), consider: (a) quantized int8 version, (b) lightweight Gemma 1b, (c) fallback to rules-based responses

## MediaPipe LLM Model Format

The `.task` format is MediaPipe's optimized binary format containing:
- Quantized model weights (int4 = 8x smaller than fp32)
- Tokenizers embedded
- Configuration for inference

Files in this format:
- Cannot be mixed with ONNX or TF Lite .tflite files
- Must use `com.google.mediapipe:tasks-genai` library for inference
- Are immutable after download (integrity check recommended)

## Future: Lightweight Fallback

If low-RAM devices become a major issue, consider:
1. Gemma 1B (smaller, faster)
2. Rules-based classifier + simple templates (no model needed)
3. Progressive: Try Gemma, fall back to rules if OOM

This would be in a separate branch and controlled by a feature flag.
