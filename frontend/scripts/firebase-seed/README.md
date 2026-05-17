# Firebase korak_pool seed

Ova skripta puni Realtime Database kolekciju `korak_pool` sa par primjera.

## Preuslovi

- Firebase Realtime Database je omogucen
- Imas service account JSON (Project Settings -> Service accounts -> Generate new private key)

## Instalacija

```bash
cd /home/mare/Desktop/e2-ma-tim22-2026/frontend/scripts/firebase-seed
npm install
```

## Validacija seed fajla

```bash
npm run validate
```

## Seed u bazu

```bash
export FIREBASE_DATABASE_URL="https://slagalica-mobilna-aplikacija-default-rtdb.europe-west1.firebasedatabase.app"
export GOOGLE_APPLICATION_CREDENTIALS="/putanja/do/service-account.json"

npm run seed
```

## Napomena

Ako zelis da se samo merge-uje u postojeci `korak_pool`, pokreni:

```bash
MERGE=true npm run seed
```
