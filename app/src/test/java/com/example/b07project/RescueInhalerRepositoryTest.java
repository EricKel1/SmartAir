package com.example.b07project;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

import com.example.b07project.models.RescueInhalerLog;
import com.example.b07project.repository.RescueInhalerRepository;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(MockitoJUnitRunner.class)
public class RescueInhalerRepositoryTest {
    
    @Mock
    private FirebaseFirestore mockDb;
    
    @Mock
    private CollectionReference mockCollection;
    
    @Mock
    private DocumentReference mockDocument;
    
    @Mock
    private Task<DocumentReference> mockAddTask;
    
    @Mock
    private Query mockQuery;
    
    @Mock
    private Task<QuerySnapshot> mockQueryTask;
    
    private RescueInhalerLog testLog;
    
    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        
//        testLog = new RescueInhalerLog(
//            "user123",
//            new Date(),
//            2,
//            "Test notes"
//        );
    }
    
    @Test
    public void testSaveLogCallbackInterface() {
        RescueInhalerRepository.SaveCallback callback = new RescueInhalerRepository.SaveCallback() {
            @Override
            public void onSuccess(String documentId) {
                assertEquals("Document ID should match", "doc123", documentId);
            }
            
            @Override
            public void onFailure(String error) {
                fail("Should not call onFailure");
            }
        };
        
        callback.onSuccess("doc123");
    }
    
    @Test
    public void testLoadCallbackInterface() {
        RescueInhalerRepository.LoadCallback callback = new RescueInhalerRepository.LoadCallback() {
            @Override
            public void onSuccess(java.util.List<RescueInhalerLog> logs) {
                assertNotNull("Logs list should not be null", logs);
            }
            
            @Override
            public void onFailure(String error) {
                fail("Should not call onFailure");
            }
        };
        
        callback.onSuccess(new java.util.ArrayList<>());
    }
    
    @Test
    public void testSaveLogWithNullNotes() {
//        RescueInhalerLog logWithoutNotes = new RescueInhalerLog(
//            "user123",
//            new Date(),
//            1,
//            null
//        );
        
//        assertNull("Notes should be null", logWithoutNotes.getNotes());
    }
    
    @Test
    public void testLoadCallbackWithEmptyList() {
        final boolean[] successCalled = {false};
        
        RescueInhalerRepository.LoadCallback callback = new RescueInhalerRepository.LoadCallback() {
            @Override
            public void onSuccess(java.util.List<RescueInhalerLog> logs) {
                successCalled[0] = true;
                assertNotNull("Logs list should not be null", logs);
                assertEquals("Logs list should be empty", 0, logs.size());
            }
            
            @Override
            public void onFailure(String error) {
                fail("Should not fail");
            }
        };
        
        callback.onSuccess(new java.util.ArrayList<>());
        assertTrue("Success callback should be called", successCalled[0]);
    }
    
    @Test
    public void testLoadCallbackWithMultipleLogs() {
        final boolean[] successCalled = {false};
        
        java.util.List<RescueInhalerLog> testLogs = new java.util.ArrayList<>();
//        testLogs.add(new RescueInhalerLog("user1", new Date(), 1, "Note 1"));
//        testLogs.add(new RescueInhalerLog("user1", new Date(), 2, "Note 2"));
//        testLogs.add(new RescueInhalerLog("user1", new Date(), 3, "Note 3"));
        
        RescueInhalerRepository.LoadCallback callback = new RescueInhalerRepository.LoadCallback() {
            @Override
            public void onSuccess(java.util.List<RescueInhalerLog> logs) {
                successCalled[0] = true;
                assertNotNull("Logs list should not be null", logs);
                assertEquals("Should have 3 logs", 3, logs.size());
            }
            
            @Override
            public void onFailure(String error) {
                fail("Should not fail");
            }
        };
        
        callback.onSuccess(testLogs);
        assertTrue("Success callback should be called", successCalled[0]);
    }
    
    @Test
    public void testFailureCallback() {
        final boolean[] failureCalled = {false};
        final String expectedError = "Network error";
        
        RescueInhalerRepository.SaveCallback callback = new RescueInhalerRepository.SaveCallback() {
            @Override
            public void onSuccess(String documentId) {
                fail("Should not succeed");
            }
            
            @Override
            public void onFailure(String error) {
                failureCalled[0] = true;
                assertEquals("Error message should match", expectedError, error);
            }
        };
        
        callback.onFailure(expectedError);
        assertTrue("Failure callback should be called", failureCalled[0]);
    }
}
