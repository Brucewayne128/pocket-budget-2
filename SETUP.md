# Pocket Budget — setup guide (GitHub version)

GitHub builds the Android app (APK) and hosts the live, view-only page that QR codes open. Firebase stores each budget. Both are free, and you can do all of this from a phone or a computer.

- **site/** — the budget screens and the view-only page
- **android/** — the Android app GitHub turns into the APK
- **database.rules.json** — the security rules you paste into Firebase
- **.github/workflows/build.yml** — the instructions GitHub follows to build everything

---

## Step 1 — GitHub (about 5 minutes)

1. Go to **github.com** and create a free account. Remember your **username**.
2. Tap **+ → New repository**.
   - Name: `pocket-budget`
   - Choose **Public**. Free GitHub Pages hosting needs a public repository; your budget data is *not* stored here.
   - Leave README, .gitignore and license **unchecked**.
   - Tap **Create repository**.
3. In the new repository, open **Settings → Pages**. Under **Build and deployment → Source**, choose **GitHub Actions**.

## Step 2 — Firebase (about 10 minutes)

1. Go to **console.firebase.google.com**, sign in with Google, and tap **Create a project**. Name it something like `pocket-budget`. You can turn Analytics off.
2. **Build → Authentication → Get started → Sign-in method → Anonymous** → turn it on → **Save**.
3. Still in Authentication, open **Settings → Authorized domains → Add domain** and enter `YOUR-GITHUB-USERNAME.github.io`.
4. **Build → Realtime Database → Create database** → pick a location → **Start in locked mode** → **Enable**.
5. Open the **Rules** tab, delete what's there, paste the whole contents of `database.rules.json`, and tap **Publish**.
6. Tap the gear icon → **Project settings**. Under **Your apps**, tap the **</>** (Web) icon, give it any nickname, and tap **Register app**. Copy the `firebaseConfig` block it shows. It looks like this:
   ```
   const firebaseConfig = {
     apiKey: "...",
     authDomain: "...",
     databaseURL: "...",
     projectId: "...",
     ...
   };
   ```
   If `databaseURL` is missing, copy it from the top of the **Realtime Database** page. It ends in `firebasedatabase.app` or `firebaseio.com`.

## Step 3 — Put the code in your repository

**Easiest:** send Claude your GitHub username and the `firebaseConfig` block. Claude can fill in `site/config.js` and upload everything to your repository for you.

**By yourself:** paste your settings into `site/config.js`, then in your repository tap **Add file → Upload files** and upload everything in this folder. Keep the folder structure, including the `.github` folder.

## Step 4 — GitHub builds the app (about 5 minutes)

Every upload starts a build automatically. Open the **Actions** tab in your repository to watch it. When both parts show a green check:

- **The APK download link** is
  `https://github.com/YOUR-GITHUB-USERNAME/pocket-budget/releases/latest/download/PocketBudget.apk`
  You can text this link to any Android phone.
- **The website** is `https://YOUR-GITHUB-USERNAME.github.io/pocket-budget/`. It's what QR codes open.

## Step 5 — Install on an Android phone

1. Tap the download link and download **PocketBudget.apk**.
2. Open the file. Android will say it blocked an unknown app. Tap **Settings**, turn on **Allow from this source**, and go back.
3. Tap **Install**. If Play Protect warns you, tap **More details → Install anyway**. It warns about any app that isn't from the Play Store.

## Using it

- Every phone that installs the app gets its **own separate, empty budget**: no bills, no expenses, and Gas, Food and Other categories with no amounts set. Each person adds their own bills and budgets with **Edit bills** and **Edit categories**.
- At the bottom of the **Everything else** page is that phone's **QR code**. Anyone who scans it with an iPhone or Android camera sees that budget live, but **can't change anything**.
- **Send the link** texts the same view-only link.
- Only the phone that created a budget can edit it.

## Good to know

- **Updates keep your budget.** Every APK GitHub builds is signed with the same key (`android/app/pocketbudget.jks`), so installing a newer one keeps your data.
- **Don't uninstall the app or clear its storage.** If you do, that phone starts a new, empty budget. The old budget's QR link still works for viewing, but it can't be edited anymore.
- **Anyone with your QR code or link can see your budget.** Only share it with people you trust.
- **Changing the screens** only needs a change to `site/index.html`. The website updates within a few minutes, and the app picks it up next time it opens, with no reinstall.
- **The free Firebase plan** is plenty for a family or a group of friends: about 100 phones connected at once and 10 GB of downloads a month.
