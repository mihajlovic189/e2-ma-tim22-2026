const fs = require("fs");
const path = require("path");
const admin = require("firebase-admin");

function fail(message) {
  console.error(message);
  process.exit(1);
}

const databaseUrl = process.env.FIREBASE_DATABASE_URL;
if (!databaseUrl) {
  fail("FIREBASE_DATABASE_URL is not set");
}

const serviceAccountPath =
  process.env.SERVICE_ACCOUNT_PATH || process.env.GOOGLE_APPLICATION_CREDENTIALS;
if (!serviceAccountPath) {
  fail("SERVICE_ACCOUNT_PATH or GOOGLE_APPLICATION_CREDENTIALS is not set");
}

const serviceAccount = JSON.parse(
  fs.readFileSync(path.resolve(serviceAccountPath), "utf8")
);

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
  databaseURL: databaseUrl,
});

const dataPath = path.join(__dirname, "seed-spojnice-data.json");
const seedData = JSON.parse(fs.readFileSync(dataPath, "utf8"));

const spojnicePool = seedData.Spojnice || {};
const ref = admin.database().ref("Spojnice");

const useMerge = String(process.env.MERGE).toLowerCase() === "true";
const write = useMerge ? ref.update(spojnicePool) : ref.set(spojnicePool);

write
  .then(() => {
    console.log(`Seeded Spojnice with ${Object.keys(spojnicePool).length} items`);
    process.exit(0);
  })
  .catch((err) => {
    console.error("Seed failed:", err);
    process.exit(1);
  });

