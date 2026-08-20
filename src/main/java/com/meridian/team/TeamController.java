package com.meridian.team;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * README §6.3 Team
 */
@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    @PostMapping
    public ResponseEntity<TeamResponse> createTeam(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestBody(required = false) TeamCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(teamService.createTeam(authorizationHeader, request));
    }

    @GetMapping
    public ResponseEntity<List<TeamResponse>> listTeams(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader
    ) {
        return ResponseEntity.ok(teamService.listTeams(authorizationHeader));
    }

    @GetMapping("/{teamId}")
    public ResponseEntity<TeamResponse> getTeam(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @PathVariable Long teamId
    ) {
        return ResponseEntity.ok(teamService.getTeam(authorizationHeader, teamId));
    }

    @PatchMapping("/{teamId}")
    public ResponseEntity<TeamResponse> updateTeam(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @PathVariable Long teamId,
            @RequestBody(required = false) TeamUpdateRequest request
    ) {
        return ResponseEntity.ok(teamService.updateTeam(authorizationHeader, teamId, request));
    }

    @DeleteMapping("/{teamId}")
    public ResponseEntity<Void> deleteTeam(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @PathVariable Long teamId
    ) {
        teamService.deleteTeam(authorizationHeader, teamId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{teamId}/pm-transfer")
    public ResponseEntity<TeamMemberResponse> transferPm(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @PathVariable Long teamId,
            @RequestBody(required = false) TeamPmTransferRequest request
    ) {
        return ResponseEntity.ok(teamService.transferPm(authorizationHeader, teamId, request));
    }

    @DeleteMapping("/{teamId}/leave")
    public ResponseEntity<Void> leaveTeam(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @PathVariable Long teamId
    ) {
        teamService.leaveTeam(authorizationHeader, teamId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{teamId}/members")
    public ResponseEntity<List<TeamMemberResponse>> listMembers(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @PathVariable Long teamId
    ) {
        return ResponseEntity.ok(teamService.listMembers(authorizationHeader, teamId));
    }

    @PostMapping("/{teamId}/members")
    public ResponseEntity<TeamMemberResponse> addMember(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @PathVariable Long teamId,
            @RequestBody(required = false) TeamMemberAddRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(teamService.addMember(authorizationHeader, teamId, request));
    }

    @DeleteMapping("/{teamId}/members/{userId}")
    public ResponseEntity<Void> removeMember(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @PathVariable Long teamId,
            @PathVariable Long userId
    ) {
        teamService.removeMember(authorizationHeader, teamId, userId);
        return ResponseEntity.noContent().build();
    }
}
