package com.scherule.calendaring;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import static com.google.inject.name.Names.named;


public class CalendaringApplicationContext extends AbstractModule {

    private final Injector injector = Guice.createInjector(this, new CalendaringDomainContext());

    Injector getInjector() {
        return injector;
    }

    @Override
    protected void configure() {
        configureChannel();
    }

    private void configureChannel() {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        factory.setPort(5672);
        try {
            Connection connection = factory.newConnection();
            Channel channel = connection.createChannel();
            bind(Channel.class).annotatedWith(named("scheduling.channel")).toInstance(channel);
        } catch (Exception e) {
            throw new IllegalStateException("Could not connect to scheduling channel", e);
        }
    }

}
