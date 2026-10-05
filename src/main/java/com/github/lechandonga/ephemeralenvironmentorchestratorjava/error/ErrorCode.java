package com.github.lechandonga.ephemeralenvironmentorchestratorjava.error;

import org.springframework.http.HttpStatus;

/**
 * 业务失败原因。每种原因使用独立的错误码，调用方可明确区分。
 */
public enum ErrorCode {

    /** 环境不存在（已回收终态后查询也走该类“资源不可用”分支，见 ENV_RECLAIMED / ENV_RELEASING）。 */
    ENV_NOT_FOUND(HttpStatus.NOT_FOUND),

    /** 状态机非法流转，例如 RECLAIMED 被试图改回 READY。 */
    ILLEGAL_STATE_TRANSITION(HttpStatus.CONFLICT),

    /** 团队同时占用环境数超过配额上限。 */
    QUOTA_EXCEEDED(HttpStatus.CONFLICT),

    /** 同一环境已有并发申请在途，本次抢占失败。 */
    ALREADY_REQUESTING(HttpStatus.CONFLICT),

    /** 操作人不属于环境归属团队。 */
    TEAM_MISMATCH(HttpStatus.FORBIDDEN),

    /** 环境已过期，续租不再被允许。 */
    ALREADY_EXPIRED(HttpStatus.CONFLICT),

    /** 环境尚未就绪（申请中），暂不可续租。 */
    NOT_READY(HttpStatus.CONFLICT),

    /** 回收已开始（回收中），续租/查询拿不到可用环境。 */
    ENV_RELEASING(HttpStatus.CONFLICT),

    /** 环境已被回收（终态），续租/查询拿不到可用环境。 */
    ENV_RECLAIMED(HttpStatus.GONE),

    /** 请求参数不合法。 */
    INVALID_REQUEST(HttpStatus.BAD_REQUEST);

    private final HttpStatus httpStatus;

    ErrorCode(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
