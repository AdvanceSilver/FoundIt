FOUNDIT ANDROID APP - CODEASSIST SETUP

WHAT THIS STARTER DOES
- Native Android app: Kotlin + XML.
- Supabase email/password sign-up and sign-in.
- Online Lost and Found reports.
- Photo upload to Supabase Storage.
- Search and category filtering.
- Separate Lost and Found feeds.
- Dark Liquid Glass-inspired layout.

IMPORTANT LIMITATIONS OF THIS FIRST WORKING VERSION
- It is a working foundation, not the final full social platform.
- Email confirmation depends on your Supabase Auth settings.
- Photo URLs are stored and uploaded; this starter does not yet load remote photos into feed cards.
- In-app chat, push notifications, match suggestions, report moderation, and account profile screens are not included yet.
- Do not publish phone numbers, addresses, identity numbers, or private details in descriptions.
- The public photo bucket means uploaded item photos are viewable by anyone who has the URL. Do not upload documents showing private information.

PART 1 - CREATE SUPABASE BACKEND
1. Open https://supabase.com and create a NEW project named FoundIt.
2. Wait until the project is ready.
3. Open SQL Editor -> New query.
4. Open the file supabase_setup.sql from this project.
5. Copy all SQL and paste it into the SQL Editor.
6. Press Run. Check that it completes without an error.
7. Open Project Settings -> API.
8. Copy the Project URL and anon/public key. Do NOT copy service_role.

PART 2 - OPEN IN CODEASSIST
1. Extract FoundItAndroid.zip on your phone.
2. In CodeAssist, choose Open Project / Open Folder.
3. Select the extracted FoundItAndroid folder (the folder containing settings.gradle.kts).
4. Let Gradle sync. If prompted, allow the project to download dependencies.
5. If CodeAssist asks for a Gradle JDK, choose JDK 17.
6. Open app/src/main/java/com/foundit/app/SupabaseConfig.kt.
7. Replace YOUR_PROJECT_ID in URL with your Supabase project reference.
   Example format: https://abcdefghijk.supabase.co
8. Replace YOUR_SUPABASE_ANON_KEY with the anon/public key you copied.
9. Save the file.

PART 3 - BUILD AND TEST
1. Build / Run the app.
2. Create an account using an email you can access and a password of at least 6 characters.
3. If Supabase asks for email confirmation, open your email, confirm, then return and sign in.
4. Tap Report Lost or Report Found.
5. Enter a title, choose a category, enter a general location, and optionally select a photo.
6. Publish the report.
7. Return to the home screen and check that the report appears.
8. Sign in on another device/account to test that reports are shared online.

IF BUILD FAILS
- Confirm CodeAssist supports Android Gradle Plugin 8.6.1 and Kotlin 2.0.21.
- Set Gradle JDK to 17.
- Confirm internet is available.
- If CodeAssist cannot sync this plugin version, send me the exact error screenshot; do not randomly change versions.

SECURITY
- Keep Row Level Security enabled.
- Never put service_role / secret keys in the Android app.
- The anon key is designed to be public; RLS policies protect data.
- Do not add personal phone numbers or home addresses to public report descriptions.
