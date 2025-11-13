const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

// Trigger when a new notification is written to Realtime Database
exports.sendPushNotification = functions.database
  .ref("/notifications/{toUserId}/{notificationId}")
  .onCreate(async (snapshot, context) => {
    const notification = snapshot.val();
    const toUserId = context.params.toUserId;

    // Get the recipient’s FCM token
    const userSnap = await admin.database().ref(`/userInfo/${toUserId}/fcmToken`).once("value");
    const token = userSnap.val();

    if (token) {
      const message = {
        token: token,
        notification: {
          title: "New " + (notification.type || "Update"),
          body: notification.message || "You have a new notification",
        },
        data: {
          type: notification.type || "",
        },
      };

      try {
        await admin.messaging().send(message);
        console.log("✅ Notification sent to:", toUserId);
      } catch (error) {
        console.error("❌ Error sending notification:", error);
      }
    } else {
      console.log("⚠️ No token for user:", toUserId);
    }

    return null;
  });
