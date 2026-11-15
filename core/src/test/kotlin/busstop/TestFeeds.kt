package busstop

import java.nio.file.Path
import kotlin.io.path.writeText

/** Writes the boarding-rules test feed used by [BoardingRulesTest] and [FeedSubsetTest]. */
fun writeBoardingRulesFeed(dir: Path) {
    dir.resolve("stops.txt").writeText("stop_id,stop_name\nS1,Test Stop\nS2,Other\nS3,Further\n")
    dir.resolve("routes.txt").writeText("route_id,route_short_name\nR1,L1\n")
    dir.resolve("trips.txt").writeText(
        "trip_id,route_id,service_id,trip_headsign\n" +
        "T_NORMAL,R1,SVC,Dest\nT_NO_PICKUP,R1,SVC,Dest\n" +
        "T_LAST_STOP,R1,SVC,Dest\nT_CONTINUES,R1,SVC,Dest\n"
    )
    dir.resolve("stop_times.txt").writeText(
        "trip_id,departure_time,arrival_time,stop_id,stop_sequence,pickup_type\n" +
        // T_NORMAL: S1 first, continues to S2 → boardable at S1
        "T_NORMAL,08:00:00,08:00:00,S1,1,0\n" +
        "T_NORMAL,08:10:00,08:10:00,S2,2,0\n" +
        // T_NO_PICKUP: pickup_type=1 at S1 → not boardable
        "T_NO_PICKUP,08:30:00,08:30:00,S1,1,1\n" +
        "T_NO_PICKUP,08:40:00,08:40:00,S2,2,0\n" +
        // T_LAST_STOP: S1 is the highest stop_sequence → not boardable
        "T_LAST_STOP,09:00:00,09:00:00,S2,1,0\n" +
        "T_LAST_STOP,09:10:00,09:10:00,S1,2,0\n" +
        // T_CONTINUES: S2 → S1 → S3; S1 is not the last stop → boardable
        "T_CONTINUES,09:30:00,09:30:00,S2,1,0\n" +
        "T_CONTINUES,09:40:00,09:40:00,S1,2,0\n" +
        "T_CONTINUES,09:50:00,09:50:00,S3,3,0\n"
    )
    dir.resolve("calendar_dates.txt").writeText("service_id,date,exception_type\nSVC,20261113,1\n")
}
