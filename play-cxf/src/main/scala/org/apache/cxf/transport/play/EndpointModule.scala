package org.apache.cxf.transport.play

import javax.inject.{Inject, Provider, Singleton}

import com.google.inject.name.Names
import com.typesafe.config.{Config, ConfigFactory, ConfigObject}
import org.apache.cxf.binding.soap.{SoapBindingConfiguration, SoapVersion}
import org.apache.cxf.config.DynamicConfig
import org.apache.cxf.jaxws.EndpointImpl
import org.apache.cxf.transport.DestinationFactoryManager
import org.apache.cxf.{Bus, CoreModule}
import play.api.Configuration

import scala.collection.JavaConverters._

abstract class EndpointModule extends CoreModule {
  import EndpointModule._

  protected def bindPlayTransport(): Unit = {
    bindBus()

    bind(classOf[PlayTransportFactory])
      .toProvider(classOf[PlayTransportFactoryProvider])
      .asEagerSingleton()
  }

  protected def bindEndpoint(key: String, wrappers: Seq[Class[_ <: EndpointWrapper]] = Seq.empty): Unit = {
    bindPlayTransport()

    bind(classOf[EndpointImpl])
      .annotatedWith(Names.named(key))
      .toProvider(new EndpointImplProvider(key, wrappers))
      .asEagerSingleton()
  }
}

object EndpointModule {
  final val EndpointKeyConfig = "apache.cxf.play.endpoint"

  @Singleton
  private class PlayTransportFactoryProvider @Inject() (
    bus: Bus,
    configuration: Configuration
  ) extends Provider[PlayTransportFactory] {
    def get(): PlayTransportFactory = {
      val factory = new PlayTransportFactory

      configuration.getOptional[Seq[String]]("apache.cxf.play.transports")
        .map(_.asJava)
        .foreach(factory.setTransportIds)

      val dfm = bus.getExtension(classOf[DestinationFactoryManager])

      factory.getTransportIds.asScala.foreach(dfm.registerDestinationFactory(_, factory))

      factory
    }
  }

  @Singleton
  private class EndpointImplProvider(key: String, wrappers: Seq[Class[_ <: EndpointWrapper]] = Seq.empty) extends Provider[EndpointImpl] {
    @Inject var bus: Bus = _
    @Inject var injector: play.api.inject.Injector = _
    @Inject var configuration: Configuration = _

    def get(): EndpointImpl = {
      val config = configuration.getOptional[Configuration](EndpointKeyConfig)
        .flatMap(_.getOptional[ConfigObject](key))
        .getOrElse(ConfigFactory.empty.root)

      val implementorClazz = Thread.currentThread().getContextClassLoader.loadClass(
        config.toConfig.getString("implementor")
      )

      val endpoint = new EndpointImpl(bus, injector.instanceOf(implementorClazz))

      val dynamicConfig = new DynamicConfig(config.toConfig)
      dynamicConfig.bindingConfig.asOption[Config].map(new DynamicConfig(_)).map { config =>
        val bindingConfig = new SoapBindingConfiguration

        config.version.asOption[SoapVersion].foreach(bindingConfig.setVersion)

        bindingConfig
      }.foreach(endpoint.setBindingConfig)

      wrappers.foreach { wrapperClass =>
        injector.instanceOf(wrapperClass).callback(endpoint)
      }

      endpoint.publish(config.toConfig.getString("address"))

      endpoint
    }
  }
}
