package ru.lopon.workout;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import ru.lopon.auth.CurrentUser;
import ru.lopon.config.OpenApiConfig;
import ru.lopon.web.ApiException;
import ru.lopon.workout.WorkoutDtos.PageResponse;
import ru.lopon.workout.WorkoutDtos.ReanalyzeResult;
import ru.lopon.workout.WorkoutDtos.StoredFile;
import ru.lopon.workout.WorkoutDtos.SummaryResponse;
import ru.lopon.workout.WorkoutDtos.TrackPointResponse;
import ru.lopon.workout.WorkoutDtos.WorkoutDetails;
import ru.lopon.workout.WorkoutDtos.WorkoutItem;
import ru.lopon.workout.WorkoutDtos.WorkoutPatch;

@RestController
@RequestMapping("/api/workouts")
@Tag(name = "Тренировки", description = "Загрузка FIT, список, разбор, правка названия и описания, удаление")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class WorkoutController {

    private final WorkoutService workouts;

    public WorkoutController(WorkoutService workouts) {
        this.workouts = workouts;
    }

    @Operation(summary = "Загрузить FIT-файл: 201 — разобран, 409 — уже загружен, 422 — не FIT")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<WorkoutDetails> upload(CurrentUser user, @RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Пустой файл");
        }
        String filename = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank() ? "workout.fit" : file.getOriginalFilename();
        long id = workouts.upload(user.id(), filename, file.getBytes());
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(id))
                .body(workouts.details(user.id(), id));
    }

    @Operation(summary = "Свои тренировки, новые сверху")
    @GetMapping
    public PageResponse<WorkoutItem> list(CurrentUser user,
                                          @RequestParam(defaultValue = "0") @Min(0) int page,
                                          @RequestParam(defaultValue = "50") @Min(1) @Max(500) int size) {
        return workouts.list(user.id(), page, size);
    }

    @Operation(summary = "Тренировка: метаданные, сводка и полный разбор")
    @GetMapping("/{id}")
    public WorkoutDetails get(CurrentUser user, @PathVariable long id) {
        return workouts.details(user.id(), id);
    }

    @Operation(summary = "Изменить название и описание")
    @PatchMapping("/{id}")
    public WorkoutDetails patch(CurrentUser user, @PathVariable long id, @Valid @RequestBody WorkoutPatch patch) {
        return workouts.patch(user.id(), id, patch);
    }

    @Operation(summary = "Удалить тренировку вместе с исходным файлом и точками")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(CurrentUser user, @PathVariable long id) {
        workouts.delete(user.id(), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Метрики разбора")
    @GetMapping("/{id}/summary")
    public SummaryResponse summary(CurrentUser user, @PathVariable long id) {
        return workouts.summaryOf(user.id(), id);
    }

    @Operation(summary = "Прореженный ряд точек для графиков и трека")
    @GetMapping("/{id}/track-points")
    public List<TrackPointResponse> trackPoints(CurrentUser user, @PathVariable long id) {
        return workouts.trackPoints(user.id(), id);
    }

    @Operation(summary = "Исходный FIT-файл")
    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> file(CurrentUser user, @PathVariable long id) {
        StoredFile file = workouts.file(user.id(), id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.filename(), StandardCharsets.UTF_8).build().toString())
                .body(file.content());
    }

    @Operation(summary = "Пересчитать свои тренировки по текущим настройкам")
    @PostMapping("/reanalyze")
    public ReanalyzeResult reanalyze(CurrentUser user) {
        return workouts.reanalyze(user.id());
    }
}
