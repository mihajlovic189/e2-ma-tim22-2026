const functions = require('firebase-functions/v1');
const admin = require('firebase-admin');

admin.initializeApp();

/**
 * Triggered on every new chat message in RTDB.
 * Writes a notification document to Firestore for every user in the same region
 * (excluding the sender). The sendDirectNotification function below then sends
 * each user a direct FCM so the device can show a system notification.
 */
exports.sendChatNotification = functions.database
    .ref('/regional_chats/{region}/{msgId}')
    .onCreate(async (snapshot, context) => {
        const message = snapshot.val();
        if (!message) return null;

        const senderId   = message.senderId   || '';
        const senderName = message.senderName || 'Nepoznat';
        const text       = message.text       || '';
        const region     = context.params.region;
        const title      = 'Nova poruka od: ' + senderName;
        const timestamp  = Date.now();

        try {
            const usersSnap = await admin.firestore()
                .collection('users')
                .where('region', '==', region)
                .get();

            const msgId = context.params.msgId;
            const writes = usersSnap.docs
                .filter(doc => doc.id !== senderId)
                .map(doc =>
                    // Use the RTDB message ID as the Firestore document ID so that if
                    // the client-side RTDB listener already wrote the document, this
                    // becomes a no-op update rather than creating a duplicate.
                    admin.firestore()
                        .collection('users').doc(doc.id)
                        .collection('notifications').doc(msgId)
                        .set({ title, body: text, type: 'chat', timestamp, read: false },
                             { merge: true })
                );

            if (writes.length > 0) {
                await Promise.all(writes);
                console.log(`Chat notification saved for ${writes.length} user(s) in region "${region}"`);
            } else {
                console.log(`No other users found in region "${region}"`);
            }
        } catch (error) {
            console.error('Error saving chat notifications to Firestore:', error);
        }

        return null;
    });

/**
 * Triggered whenever a new notification document is created for any user.
 * Looks up the user's FCM token and sends a direct high-priority FCM so the
 * device can show a system notification even when the app is killed.
 * The "source: direct_fcm" flag tells onMessageReceived NOT to re-save to Firestore.
 */
exports.sendDirectNotification = functions.firestore
    .document('users/{uid}/notifications/{notifId}')
    .onCreate(async (snap, context) => {
        const data = snap.data();
        if (!data) return null;

        const uid = context.params.uid;

        const userDoc = await admin.firestore().collection('users').doc(uid).get();
        if (!userDoc.exists) return null;
        const fcmToken = userDoc.data().fcmToken;
        if (!fcmToken) return null;

        const message = {
            token: fcmToken,
            data: {
                title:  data.title || '',
                body:   data.body  || '',
                type:   data.type  || 'other',
                source: 'direct_fcm'  // tells onMessageReceived not to re-save to Firestore
            },
            android: { priority: 'high' }
        };

        try {
            await admin.messaging().send(message);
            console.log(`Direct FCM sent to user ${uid} for type "${data.type}"`);
        } catch (error) {
            console.error('Failed to send direct FCM:', error);
        }

        return null;
    });
