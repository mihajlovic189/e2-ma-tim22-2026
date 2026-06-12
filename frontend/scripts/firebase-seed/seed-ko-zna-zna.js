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

const dataPath = path.join(__dirname, "seed-ko-zna-zna-data.json");
const seedData = JSON.parse(fs.readFileSync(dataPath, "utf8"));

const koZnaZnaPool = seedData.KoZnaZna || {};
const ref = admin.database().ref("KoZnaZna");

const useMerge = String(process.env.MERGE).toLowerCase() === "true";
const write = useMerge ? ref.update(koZnaZnaPool) : ref.set(koZnaZnaPool);

write
  .then(() => {
    console.log(`Seeded KoZnaZna with ${Object.keys(koZnaZnaPool).length} items`);
    process.exit(0);
  })
  .catch((err) => {
    console.error("Seed failed:", err);
    process.exit(1);
  });

