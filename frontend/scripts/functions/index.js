const functions = require('firebase-functions/v1');
const admin = require('firebase-admin');

admin.initializeApp();

/**
 * Triggered whenever a new message is written to regional_chats/{region}/{msgId}.
 * Sends an FCM push notification to the topic "chat_{region}" so all devices
 * subscribed to that topic (i.e. users who left the chat screen) receive it.
 */
exports.sendChatNotification = functions.database
    .ref('/regional_chats/{region}/{msgId}')
    .onCreate(async (snapshot, context) => {
        const message = snapshot.val();
        if (!message) return null;

        const senderId  = message.senderId  || '';
        const senderName = message.senderName || 'Nepoznat';
        const text       = message.text       || '';
        const region     = context.params.region;

        // Topic name must match what RegionalChatFragment subscribes to
        const topicName = 'chat_' + region.replace(/ /g, '_');

        const fcmPayload = {
            // data fields are read by MyFirebaseMessagingService on the device
            data: {
                senderId,
                senderName,
                text,
                type: 'chat'
            },
            notification: {
                title: 'Nova poruka od: ' + senderName,
                body: text
            }
        };

        try {
            await admin.messaging().sendToTopic(topicName, fcmPayload);
            console.log(`FCM sent to topic "${topicName}" for region "${region}"`);
        } catch (error) {
            console.error('Failed to send FCM to topic:', error);
        }

        return null;
    });
