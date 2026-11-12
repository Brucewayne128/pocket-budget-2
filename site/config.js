// Your Firebase project settings. These are safe to be public:
// the database security rules are what protect each budget.
window.FIREBASE_CONFIG = {
  apiKey: "AIzaSyAgXQu-2SkM-4Was--B_MN2NvqItj-CuGQ",
  authDomain: "pocket-budget-28077.firebaseapp.com",
  databaseURL: "https://pocket-budget-28077-default-rtdb.firebaseio.com",
  projectId: "pocket-budget-28077",
  storageBucket: "pocket-budget-28077.firebasestorage.app",
  messagingSenderId: "558526915496",
  appId: "1:558526915496:web:c9feeaf701056b8a14739d"
};
if (!String(window.FIREBASE_CONFIG.apiKey).startsWith("PASTE")) {
  firebase.initializeApp(window.FIREBASE_CONFIG);
}
