package com.glassbox.hello.notifications

object NotificationPrefs {
    const val PREFS_NAME = "hello_settings"

    const val CHANNEL_CALLS = "calls"
    const val CHANNEL_ONGOING_CALLS = "ongoing_calls"
    const val CHANNEL_MISSED_CALLS = "missed_calls"
    const val CHANNEL_MESSAGES = "messages"
    const val CHANNEL_MENTIONS = "mentions"
    const val CHANNEL_STATUS_POSTS = "status_posts"
    const val CHANNEL_STATUS_ACTIVITY = "status_activity"
    const val CHANNEL_SYSTEM = "system"
    const val CHANNEL_RE_ENGAGEMENT = "re_engagement"

    const val KEY_MESSAGE_NOTIFICATIONS = "message_notifications"
    const val KEY_IN_APP_NOTIFICATIONS = "in_app_notifications"
    const val KEY_DESKTOP_NOTIFICATIONS = "desktop_notifications"
    const val KEY_CALL_NOTIFICATIONS = "call_notifications"
    const val KEY_MISSED_CALL_NOTIFICATIONS = "missed_call_notifications"
    const val KEY_MENTION_NOTIFICATIONS = "mention_notifications"
    const val KEY_STATUS_POST_NOTIFICATIONS = "status_post_notifications"
    const val KEY_STATUS_ACTIVITY_NOTIFICATIONS = "status_activity_notifications"
    const val KEY_SYSTEM_NOTIFICATIONS = "system_notifications"
    const val KEY_RE_ENGAGEMENT_NOTIFICATIONS = "re_engagement_notifications"
    const val KEY_QUIET_HOURS_ENABLED = "quiet_hours_enabled"
    const val KEY_QUIET_HOURS_START_MINUTES = "quiet_hours_start_minutes"
    const val KEY_QUIET_HOURS_END_MINUTES = "quiet_hours_end_minutes"
    const val KEY_ALLOW_CALLS_DND = "allow_calls_dnd"
    const val KEY_ALLOW_MENTIONS_DND = "allow_mentions_dnd"
}
