package com.scherule.calendaring;

import com.scherule.commons.MicroServiceVerticle
import io.vertx.rx.java.RxHelper
import io.vertx.rxjava.core.eventbus.MessageProducer
import io.vertx.rxjava.core.http.HttpServerRequest
import org.slf4j.LoggerFactory
import rx.Observable
import java.util.concurrent.TimeUnit
import io.vertx.core.http.HttpServerOptions




class CalendaringRootVerticle : MicroServiceVerticle() {

    companion object {
        val log = LoggerFactory.getLogger(CalendaringRootVerticle::class.qualifiedName)
    }

    private lateinit var httpRequestObservable: Observable<HttpServerRequest>
    private lateinit var messagePublisher: MessageProducer<String>

    override fun start() {
        super.start()
        defineMessageSource()
        defineHttpServer()

        Observable.interval(10, 10, TimeUnit.SECONDS, RxHelper.blockingScheduler(vertx))
                .subscribe({
                    messagePublisher.send("Sending $it")
                })


    }

    private fun defineMessageSource() {
        val address = "scheduling"
        rxPublishMessageSource(address, address)
                .doOnSubscribe { log.info("New subscriber for scheduling message source") }
                .doOnUnsubscribe { log.info("Subscriber has removed his subscription") }
                .doOnSuccess { log.info("[Scheduling] message source successfully published") }
                .doOnError { log.error("[Scheduling] message source could not be published") }

        messagePublisher = rxVertx.eventBus().publisher<String>(address)
    }

    private fun defineHttpServer() {
        val host = config().getString("http.host")
        val port = config().getInteger("http.port")

        httpRequestObservable = rxVertx.createHttpServer(
                HttpServerOptions().setPort(port).setHost(host)
        ).requestStream().toObservable();

        rxPublishHttpEndpoint(
                name = config().getString("http.name"),
                host = host,
                port = port
        )
                .doOnSuccess { log.info("[Scheduling] http endpoint successfully published") }
                .doOnError { log.error("[Scheduling] http endpoint could not be published") }

    }

}