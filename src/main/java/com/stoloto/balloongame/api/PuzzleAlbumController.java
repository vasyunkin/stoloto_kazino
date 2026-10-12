package com.stoloto.balloongame.api;

import com.stoloto.balloongame.api.dto.AlbumSlotResponse;
import com.stoloto.balloongame.api.dto.AlbumSyncRequest;
import com.stoloto.balloongame.service.PlayerIdentity;
import com.stoloto.balloongame.service.PuzzleAlbumService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/album")
@RequiredArgsConstructor
@Tag(name = "Album", description = "Серверный альбом пазла. Только для вошедшего игрока.")
public class PuzzleAlbumController {

    private final PuzzleAlbumService albumService;
    private final PlayerIdentity identity;

    @Operation(summary = "Собранные слоты аккаунта")
    @GetMapping
    public List<AlbumSlotResponse> list() {
        return albumService.list(identity.requireAccount());
    }

    @Operation(summary = "Идемпотентно добавить слоты. Уже занятый слот не перезаписывается.")
    @PostMapping("/sync")
    public List<AlbumSlotResponse> sync(@Valid @RequestBody AlbumSyncRequest request) {
        return albumService.sync(identity.requireAccount(), request.pieces());
    }
}
