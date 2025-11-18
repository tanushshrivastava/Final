/**
 * This file contains the AlarmConstants object, which centralizes constant values
 * used throughout the alarm-related features of the application.
 */
package com.cs407.afinal.alarm

/**
 * A singleton object holding constant values, primarily keys for intent extras.
 *
 * Using a constants object helps prevent typos and centralizes the keys used for passing
 * alarm data in [android.content.Intent] bundles between components like activities,
 * services, and broadcast receivers. This makes the code more maintainable and readable.
 */
object AlarmConstants {
    /** Key for the unique integer ID of the alarm. */
    const val EXTRA_ALARM_ID = "extra_alarm_id"

    /** Key for the string label or name of the alarm. */
    const val EXTRA_ALARM_LABEL = "extra_alarm_label"

    /** Key for the trigger time of the alarm in milliseconds since the epoch. */
    const val EXTRA_ALARM_TIME = "extra_alarm_time"

    /** Key for the boolean flag indicating if the "gentle wake" feature is enabled. */
    const val EXTRA_GENTLE_WAKE = "extra_gentle_wake"

    /** Key for the planned bedtime in milliseconds, used for sleep cycle tracking. */
    const val EXTRA_PLANNED_BEDTIME = "extra_planned_bedtime"

    /** Key for the boolean flag indicating if the alarm is set to be recurring. */
    const val EXTRA_IS_RECURRING = "extra_is_recurring"

    /** Key for an [ArrayList] of integers representing the days of the week for a recurring alarm. */
    const val EXTRA_RECURRING_DAYS = "extra_recurring_days"
}
