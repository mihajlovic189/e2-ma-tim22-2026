const fs = require("fs");
const path = require("path");

function fail(message) {
  console.error(message);
  process.exit(1);
}

const dataPath = path.join(__dirname, "seed-data.json");
const raw = fs.readFileSync(dataPath, "utf8");
const data = JSON.parse(raw);

if (!data.korak_pool || typeof data.korak_pool !== "object") {
  fail("korak_pool is missing or not an object");
}

const items = Object.entries(data.korak_pool);
if (items.length === 0) {
  fail("korak_pool has no items");
}

for (const [key, value] of items) {
  if (!value || typeof value !== "object") {
    fail(`Item ${key} is not an object`);
  }
  if (!value.solution || typeof value.solution !== "string" || !value.solution.trim()) {
    fail(`Item ${key} missing solution`);
  }
  if (!value.steps || typeof value.steps !== "object") {
    fail(`Item ${key} missing steps`);
  }
  for (let i = 0; i < 7; i++) {
    const step = value.steps[String(i)];
    if (!step || typeof step !== "string" || !step.trim()) {
      fail(`Item ${key} missing step ${i}`);
    }
  }
}

console.log(`OK: ${items.length} items validated`);
