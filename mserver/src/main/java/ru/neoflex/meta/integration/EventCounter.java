package ru.neoflex.meta.integration;

import java.util.Date;

import org.springframework.stereotype.Service;

@Service
public class EventCounter {
	private Date lastEventDateTime = new Date();
	public void event(){
		lastEventDateTime = new Date();
	}
	
	public Date getLastEventDateTime(){
		return lastEventDateTime;
	}
}
