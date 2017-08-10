package com.scherule.calendaring.verticles;

import com.google.inject.Inject;
import com.scherule.calendaring.controllers.MeetingController;
import com.scherule.commons.MicroServiceVerticle;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.rx.java.ObservableFuture;
import io.vertx.rx.java.RxHelper;
import io.vertx.rxjava.config.ConfigRetriever;
import io.vertx.rxjava.core.CompositeFuture;
import io.vertx.rxjava.core.Future;
import io.vertx.rxjava.core.buffer.Buffer;
import io.vertx.rxjava.core.file.FileSystem;
import io.vertx.rxjava.core.http.HttpServerResponse;
import io.vertx.rxjava.ext.web.Router;
import io.vertx.rxjava.ext.web.handler.BodyHandler;
import io.vertx.rxjava.ext.web.handler.CorsHandler;
import io.vertx.rxjava.ext.web.handler.StaticHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;

import static io.vertx.rxjava.core.Future.succeededFuture;

public class WebControllerVerticle extends MicroServiceVerticle {

    private static final Logger log = LoggerFactory.getLogger(WebControllerVerticle.class);

    private ConfigRetriever configReader;

    @Inject
    private MeetingController meetingController;

    @Override
    public void start(io.vertx.core.Future<Void> startFuture) {
        super.start(startFuture);
        configReader = ConfigRetriever.create(rxVertx);
        defineHttpServer(startFuture);
    }

    private void defineHttpServer(io.vertx.core.Future<Void> startFuture) {
        final Router router = Router.router(rxVertx);

        router.route().handler(BodyHandler.create());
        router.post("/meeting").handler(meetingController::postMeeting);
        router.get("/meeting/:meetingId").handler(meetingController::getMeeting);
        router.put("/meeting/:meetingId").handler(meetingController::putMeeting);
        router.delete("/meeting/:meetingId").handler(meetingController::deleteMeeting);

        CompositeFuture.all(
                configureCORS(router),
                configureSwaggerJSON(router),
                configureSwaggerUI(router),
                configureServer(router)
        )
                .rxSetHandler()
                .doOnSuccess((t) -> {
                    log.info("[Scheduling] http deployment complete");
                    startFuture.complete();
                })
                .doOnError(startFuture::fail)
                .subscribe();
    }

    private Future<Void> configureServer(Router router) {
        final Future<Void> outcome = Future.future();
        ObservableFuture<JsonObject> observable = RxHelper.observableFuture();
        configReader.getConfig(observable.toHandler());
        observable.subscribe(config -> {
            String host = config.getString("http.host", "localhost");
            int port = config.getInteger("http.port", 8080);

            rxVertx.createHttpServer(
                    new HttpServerOptions().setPort(port).setHost(host)
            ).requestHandler(router::accept).listen();

            rxPublishHttpEndpoint(
                    config.getString("http.name", "calendaring"),
                    host,
                    port
            ).doOnCompleted(() -> {
                log.info("[Scheduling] http endpoint successfully published");
                outcome.complete();
            }).doOnError(outcome::tryFail).subscribe();
        }, outcome::tryFail);
        return outcome;
    }

    private Future<Void> configureSwaggerJSON(Router router) {
        final Future<Void> outcome = Future.future();
        FileSystem fileSystem = rxVertx.fileSystem();
        ObservableFuture<Buffer> observable = RxHelper.observableFuture();
        observable.subscribe(
                swaggerFile -> {
                    router.get("/swagger.json").handler(
                            routingContext -> {
                                HttpServerResponse response = routingContext.response();
                                response.headers().add("content-type", "application/json");
                                response.end(
                                        swaggerFile.toString(String.valueOf(Charset.forName("utf-8")))
                                );
                            }
                    );
                    outcome.complete();
                },
                outcome::tryFail
        );
        fileSystem.readFile("swagger.json", observable.toHandler());
        return outcome;
    }

    private Future<Void> configureSwaggerUI(Router router) {
        router.route("/docs").handler(
                StaticHandler.create("src/main/resources/webroot/swagger-ui/index.html")
        );

        router.route("/docs/*").handler(
                StaticHandler.create("src/main/resources/webroot/swagger-ui")
        );
        return succeededFuture();
    }

    private Future<Void> configureCORS(Router router) {
        router.route().handler(CorsHandler.create("*")
                .allowedMethod(HttpMethod.GET)
                .allowedMethod(HttpMethod.POST)
                .allowedMethod(HttpMethod.OPTIONS)
                .allowedHeader("X-PINGARUNER")
                .allowedHeader("Content-Type"));
        return succeededFuture();
    }

}