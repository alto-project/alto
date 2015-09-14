package com.frs.alto.hibernate4;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.hibernate.SessionFactory;
import org.hibernate.cache.spi.RegionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.proxy.EntityNotFoundDelegate;
import org.hibernate.service.Service;
import org.hibernate.stat.spi.StatisticsImplementor;
import org.springframework.orm.hibernate4.LocalSessionFactoryBean;
import org.springframework.orm.hibernate4.LocalSessionFactoryBuilder;

public class Hibernate4SessionFactoryBean extends LocalSessionFactoryBean {
	
	private Map<String, Service> serviceMap = null;
	private EntityNotFoundDelegate entityNotFoundDelegate;
	
	private boolean loggingEntityNotFoundDelegate = false;
	
	
	
	@Override
	public void afterPropertiesSet() throws IOException {
		
		if ( (entityNotFoundDelegate == null) && loggingEntityNotFoundDelegate) {
			entityNotFoundDelegate = new LoggingEntityNotFoundDelegate();
		}
		
		super.afterPropertiesSet();
	}

	@Override
	protected SessionFactory buildSessionFactory(LocalSessionFactoryBuilder sfb) {
		
//		ServiceRegistryBuilder builder = new ServiceRegistryBuilder();

		if (entityNotFoundDelegate != null) {
		
			this.getConfiguration().setEntityNotFoundDelegate(entityNotFoundDelegate);
		}

		StandardServiceRegistryBuilder builder = new StandardServiceRegistryBuilder().
				applySettings(this.getConfiguration().getProperties());
		SessionFactory sessionFactory = this.getConfiguration().buildSessionFactory(builder.build());

		try {
			if (serviceMap != null) {
				for (Entry<String, Service> entry : serviceMap.entrySet()) {
					builder.addService(Class.forName(entry.getKey()), entry.getValue());
				}
			}
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
		
		builder.applySettings(sfb.getProperties());
//		ServiceRegistry reg = builder.buildServiceRegistry();
//		return sfb.buildSessionFactory(reg);
		return sessionFactory;
	}

	public void addService(Class serviceClass, Service implementation) {
		
		if (serviceMap == null) {
			serviceMap = new HashMap<String, Service>();
		}
		serviceMap.put(serviceClass.getName(), implementation);
	}
	
	public void setCacheRegionFactory(RegionFactory regionFactory) {
		addService(RegionFactory.class, regionFactory);
	}
	
	public void setStatisticsImplementor(StatisticsImplementor stats) {
		addService(StatisticsImplementor.class, stats);
	}

	public EntityNotFoundDelegate getEntityNotFoundDelegate() {
		return entityNotFoundDelegate;
	}

	public void setEntityNotFoundDelegate(
			EntityNotFoundDelegate entityNotFoundDelegate) {
		this.entityNotFoundDelegate = entityNotFoundDelegate;
	}

	public boolean isLoggingEntityNotFoundDelegate() {
		return loggingEntityNotFoundDelegate;
	}

	public void setLoggingEntityNotFoundDelegate(
			boolean loggingEntityNotFoundDelegate) {
		this.loggingEntityNotFoundDelegate = loggingEntityNotFoundDelegate;
	}
	
	

	
}
