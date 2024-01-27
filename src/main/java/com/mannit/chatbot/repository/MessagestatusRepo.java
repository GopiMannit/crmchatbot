
package com.mannit.chatbot.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.mannit.chatbot.model.SentMessageStatus;

public interface MessagestatusRepo extends MongoRepository<SentMessageStatus, String> {

}
