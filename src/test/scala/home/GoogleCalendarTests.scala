package home

import java.io.InputStreamReader
import java.util.Collections

import home.sparkjava.MainActor
import utest._

object GoogleCalendarTests extends TestSuite {
    val tests = Tests {
/*
        "test 1" - {
            import com.google.api.client.json.JsonFactory
            import com.google.api.client.auth.oauth2.Credential
            import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
            import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
            import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
            import com.google.api.client.http.javanet.NetHttpTransport
            import com.google.api.client.json.jackson2.JacksonFactory
            import com.google.api.client.util.DateTime
            import com.google.api.client.util.store.FileDataStoreFactory
            import com.google.api.services.calendar.Calendar
            import com.google.api.services.calendar.CalendarScopes

            val jsonFactory = JacksonFactory.getDefaultInstance

            def getCredentials(httpTransport: NetHttpTransport, jsonFactory: JsonFactory): Credential = {
                val inputStream = classOf[MainActor].getResourceAsStream("/google/credentials.json")
                val clientSecrets = GoogleClientSecrets.load(jsonFactory, new InputStreamReader(inputStream));
                val flow = new GoogleAuthorizationCodeFlow.Builder(
                    httpTransport,
                    JacksonFactory.getDefaultInstance,
                    clientSecrets,
                    Collections.singletonList(CalendarScopes.CALENDAR_READONLY)
                )
                        .setDataStoreFactory(new FileDataStoreFactory(new java.io.File("tokens")))
                        .setAccessType("offline")
                        .build()
                import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
                import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
                new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver).authorize("user")
            }

            val httpTransport: NetHttpTransport = GoogleNetHttpTransport.newTrustedTransport

            val credential = getCredentials(httpTransport, jsonFactory)
            val calendarService = new Calendar.Builder(httpTransport, jsonFactory, getCredentials(httpTransport, jsonFactory))
                    .setApplicationName("Some application name")
                    .build()
            calendarService.events().list("primary")
                    .setMaxResults(100)
                    .setTimeMin(new DateTime(System.currentTimeMillis - 1904))
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                    .execute()
                    .getItems
                    .forEach(event => {
                        println(s"Start: ${event.getStart.getDateTime} - End: ${event.getEnd.getDateTime} - Summary: " +
                                s"${event.getSummary} - Description: ${event.getDescription}")
                    })
            1
        }
*/
    }
}
