package com.github.lechandonga.ephemeralenvironmentorchestratorjava.web;

import com.github.lechandonga.ephemeralenvironmentorchestratorjava.error.BusinessException;
import com.github.lechandonga.ephemeralenvironmentorchestratorjava.error.ErrorCode;
import com.github.lechandonga.ephemeralenvironmentorchestratorjava.model.EphemeralEnvironment;
import com.github.lechandonga.ephemeralenvironmentorchestratorjava.service.ApplyResult;
import com.github.lechandonga.ephemeralenvironmentorchestratorjava.service.EnvironmentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/environments")
public class EnvironmentController {

    private final EnvironmentService environmentService;

    public EnvironmentController(EnvironmentService environmentService) {
        this.environmentService = environmentService;
    }

    /** 申请环境（同一团队同一环境名重复申请按幂等处理）。 */
    @PostMapping
    public ResponseEntity<EnvDtos.ApplyResponse> apply(
            @RequestBody EnvDtos.ApplyRequest request) {
        requireBody(request == null || request.team() == null || request.envName() == null,
                "team 和 envName 必填");
        ApplyResult result =
                environmentService.apply(request.team(), request.envName(), request.ttl());
        return ResponseEntity
                .status(result.idempotentHit() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(new EnvDtos.ApplyResponse(
                        EnvDtos.EnvView.from(result.environment()), result.idempotentHit()));
    }

    /** 续租，只能在到期前由归属团队操作。 */
    @PostMapping("/{envName}/renew")
    public EnvDtos.EnvView renew(
            @PathVariable String envName,
            @RequestParam String team,
            @RequestBody EnvDtos.RenewRequest request) {
        requireBody(request == null || request.extendBy() == null, "extendBy 必填");
        String operatorTeam = request.team() != null ? request.team() : team;
        EphemeralEnvironment env =
                environmentService.renew(operatorTeam, envName, request.extendBy());
        return EnvDtos.EnvView.from(env);
    }

    /** 主动释放。 */
    @PostMapping("/{envName}/release")
    public EnvDtos.EnvView release(
            @PathVariable String envName,
            @RequestParam(required = false) String team,
            @RequestBody(required = false) EnvDtos.ReleaseRequest request) {
        String operatorTeam = request != null && request.team() != null ? request.team() : team;
        requireBody(operatorTeam == null, "team 必填");
        EphemeralEnvironment env = environmentService.release(operatorTeam, envName);
        return EnvDtos.EnvView.from(env);
    }

    /** 查询可用环境；回收中/已回收返回明确错误码。 */
    @GetMapping("/{envName}")
    public EnvDtos.EnvView get(
            @PathVariable String envName,
            @RequestParam String team) {
        return EnvDtos.EnvView.from(environmentService.getAvailable(team, envName));
    }

    private static void requireBody(boolean invalid, String message) {
        if (invalid) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, message);
        }
    }
}
