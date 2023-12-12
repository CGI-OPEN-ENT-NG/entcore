package org.entcore.directory;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class StartAndStop extends AbstractVerticle {
    static Logger log = LoggerFactory.getLogger(StartAndStop.class);

    @Override
    public void start() throws Exception {
        super.start();
        vertx.exceptionHandler(error -> {
            log.error(String.format("[Blog] StartAndStop Error %s:%s", error.getClass().getName(), error.getMessage()));
        });
        log.info("[Blog] Start StartAndStop");
        vertx.setTimer(1500L, aLong -> {
            // Create NPE and go to the exceptionHandler
            String stringNull = null;
            stringNull.replace("", "");
        });
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        log.info("[Blog] Stop StartAndStop");
    }

    public Handler<AsyncResult<String>> registerId() {
        return stringAsyncResult -> {
            if (stringAsyncResult.succeeded()) {
                log.info("[BLOG] StartAndStop verticle id " + stringAsyncResult.result());
                vertx.setTimer(1500L, aLong -> {
                    // Undeploy verticle and go to stop methode
                    vertx.undeploy(stringAsyncResult.result());
                });
            } else {
                log.error(String.format("[Blog] Error %s:%s", stringAsyncResult.cause().getClass().getName(), stringAsyncResult.cause().getMessage()));
            }
        };
    }
}
