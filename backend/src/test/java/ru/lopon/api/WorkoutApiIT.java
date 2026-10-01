package ru.lopon.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import ru.lopon.support.Fixtures;
import ru.lopon.support.IntegrationTest;
import tools.jackson.databind.JsonNode;

class WorkoutApiIT extends IntegrationTest {

    @Test
    void fullWorkoutLifecycle() {
        Account rider = register();
        MvcTestResult created = upload(rider, "synthetic_outdoor");
        assertThat(created).hasStatus(HttpStatus.CREATED);
        long outdoor = json(created).get("id").asLong();
        assertThat(created.getResponse().getHeader("Location")).endsWith("/api/workouts/" + outdoor);
        assertThat(json(created).get("analysis").get("decoupling").get("method").asString()).isEqualTo("flat_segment");
        assertThat(upload(rider, "synthetic_trainer")).hasStatus(HttpStatus.CREATED);

        MvcTestResult duplicate = upload(rider, "synthetic_outdoor");
        assertThat(duplicate).hasStatus(HttpStatus.CONFLICT);
        assertThat(json(duplicate).get("existing_id").asLong()).isEqualTo(outdoor);

        JsonNode page = json(mvc.get().uri("/api/workouts").header("Authorization", rider.bearer()).exchange());
        assertThat(page.get("total_elements").asInt()).isEqualTo(2);
        assertThat(page.get("content").get(0).get("id").asLong()).isEqualTo(outdoor);
        assertThat(page.get("content").get(0).get("metrics").get("decoupling_method").asString()).isEqualTo("flat_segment");

        MvcTestResult patched = mvc.patch().uri("/api/workouts/" + outdoor).header("Authorization", rider.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Длинный ровный\",\"description\":\"Недосып, кофе утром\"}").exchange();
        assertThat(patched).hasStatus(HttpStatus.OK);
        assertThat(json(patched).get("title").asString()).isEqualTo("Длинный ровный");
        assertThat(json(patched).get("description").asString()).isEqualTo("Недосып, кофе утром");
        assertThat(mvc.patch().uri("/api/workouts/" + outdoor).header("Authorization", rider.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\"}")).hasStatus(HttpStatus.BAD_REQUEST);

        JsonNode summary = json(mvc.get().uri("/api/workouts/" + outdoor + "/summary").header("Authorization", rider.bearer()).exchange());
        assertThat(summary.get("metrics").get("distance_km").asDouble()).isGreaterThan(40);
        assertThat(summary.get("analysis").has("series")).isFalse();

        JsonNode points = json(mvc.get().uri("/api/workouts/" + outdoor + "/track-points").header("Authorization", rider.bearer()).exchange());
        assertThat(points.size()).isBetween(1000, 1500);
        assertThat(points.get(0).get("latitude").asDouble()).isBetween(55.0, 56.0);

        MvcTestResult file = mvc.get().uri("/api/workouts/" + outdoor + "/file").header("Authorization", rider.bearer()).exchange();
        assertThat(file).hasStatus(HttpStatus.OK);
        assertThat(file.getResponse().getContentAsByteArray()).isEqualTo(Fixtures.fit("synthetic_outdoor"));

        assertThat(mvc.delete().uri("/api/workouts/" + outdoor).header("Authorization", rider.bearer())).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().uri("/api/workouts/" + outdoor).header("Authorization", rider.bearer())).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void ridersCannotSeeEachOthersWorkouts() {
        Account owner = register();
        Account stranger = register();
        long id = json(upload(owner, "synthetic_trainer")).get("id").asLong();
        assertThat(mvc.get().uri("/api/workouts/" + id).header("Authorization", stranger.bearer())).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.get().uri("/api/workouts/" + id + "/file").header("Authorization", stranger.bearer())).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.delete().uri("/api/workouts/" + id).header("Authorization", stranger.bearer())).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(upload(stranger, "synthetic_trainer")).hasStatus(HttpStatus.CREATED);
        assertThat(json(mvc.get().uri("/api/workouts").header("Authorization", stranger.bearer()).exchange())
                .get("total_elements").asInt()).isEqualTo(1);
    }

    @Test
    void invalidUploadsAreRejected() {
        Account rider = register();
        assertThat(mvc.post().uri("/api/workouts").multipart().file(new MockMultipartFile("file", "track.gpx", "application/gpx+xml", "<gpx/>".getBytes()))
                .header("Authorization", rider.bearer())).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(mvc.post().uri("/api/workouts").multipart().file(new MockMultipartFile("file", "empty.fit", "application/octet-stream", new byte[0]))
                .header("Authorization", rider.bearer())).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void settingsAreValidatedAndDriveReanalysis() {
        Account rider = register();
        upload(rider, "synthetic_outdoor");
        JsonNode defaults = json(mvc.get().uri("/api/settings").header("Authorization", rider.bearer()).exchange());
        assertThat(defaults.get("settings").get("athlete").get("ftp").asDouble()).isEqualTo(175);
        assertThat(defaults.get("defaults").get("analysis").has("flat_net_climb_m_per_min")).isTrue();

        assertThat(mvc.put().uri("/api/settings").header("Authorization", rider.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"athlete\":{\"weight_kg\":0}}")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(mvc.put().uri("/api/settings").header("Authorization", rider.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"hr_zones\":{\"pct\":[0.6,0.7]}}")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(mvc.put().uri("/api/settings").header("Authorization", rider.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(Fixtures.golden("settings.custom.json").toString())).hasStatus(HttpStatus.OK);

        JsonNode result = json(mvc.post().uri("/api/workouts/reanalyze").header("Authorization", rider.bearer()).exchange());
        assertThat(result.get("reanalyzed").asInt()).isEqualTo(1);
        JsonNode item = json(mvc.get().uri("/api/workouts").header("Authorization", rider.bearer()).exchange()).get("content").get(0);
        assertThat(item.get("metrics").get("decoupling_pct").asDouble()).isEqualTo(-13.6);
    }

    @Test
    void weeksAndTrendsAggregateOwnRides() {
        Account rider = register();
        upload(rider, "synthetic_outdoor");
        upload(rider, "synthetic_trainer");
        JsonNode weeks = json(mvc.get().uri("/api/stats/weeks").header("Authorization", rider.bearer()).exchange()).get("weeks");
        assertThat(weeks.size()).isEqualTo(1);
        assertThat(weeks.get(0).get("week_start").asString()).isEqualTo("2026-08-31");
        assertThat(weeks.get(0).get("rides").asInt()).isEqualTo(2);
        assertThat(weeks.get(0).get("days").size()).isEqualTo(7);
        JsonNode trends = json(mvc.get().uri("/api/stats/trends").header("Authorization", rider.bearer()).exchange());
        assertThat(trends.get("rides").size()).isEqualTo(2);
        assertThat(trends.get("wkg").get("target_ftp").asInt()).isEqualTo(210);
    }

    @Test
    void openApiDescribesTheApiInSnakeCase() {
        MvcTestResult spec = mvc.get().uri("/api/openapi.json").exchange();
        assertThat(spec).hasStatus(HttpStatus.OK);
        assertThat(spec.getResponse().getContentAsByteArray()).asString().contains("access_token", "total_elements", "bearer")
                .doesNotContain("\"name\":\"user\"");
        assertThat(mvc.get().uri("/api/swagger-ui/index.html")).hasStatus(HttpStatus.OK);
    }
}
