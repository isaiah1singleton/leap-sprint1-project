package com.neueda.leap.controller.test;

import com.neueda.leap.controller.InstrumentController;
import com.neueda.leap.enums.AssetClass;
import com.neueda.leap.enums.Currency;
import com.neueda.leap.models.InstrumentResponse;
import com.neueda.leap.service.ClientService;
import com.neueda.leap.service.InstrumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class InstrumentControllerTest {
    private static final String CLIENT_EMAIL = "client@example.com";
    
    @Mock
    private InstrumentService instrumentService;
    
    @Mock
    private ClientService clientService;
    
    private InstrumentController controller;
    
    private Principal principal;
    
    private void givenActiveClient() {
        when(clientService.findActiveClientIdByEmail(CLIENT_EMAIL)).thenReturn(Optional.of(1));
    }
    
    private InstrumentResponse apple() {
        return new InstrumentResponse(
                7,
                "NASDAQ",
                "AAPL",
                "Apple Inc.",
                AssetClass.EQUITY,
                false,
                Currency.USD
        );
    }
    
    @BeforeEach
    void setUp() {
        controller = new InstrumentController(instrumentService, clientService);
        
        principal = () -> CLIENT_EMAIL;
    }
    
    @Test
    void listsInstrumentsForAnActiveClient() {
        givenActiveClient();
        
        List<InstrumentResponse> expected = List.of(apple());
        
        when(instrumentService.getInstruments()).thenReturn(expected);
        
        List<InstrumentResponse> actual = controller.getInstruments(principal);
        
        assertEquals(expected, actual);
    }
    
    @Test
    void retrnsAnEmptyCatalog() {
        givenActiveClient();
        
        when(instrumentService.getInstruments()).thenReturn(List.of());
        
        List<InstrumentResponse> actual = controller.getInstruments(principal);
        
        assertTrue(actual.isEmpty());
    }
    
    @Test
    void returnsInstrumentDetailsForAnActiveClient() {
        givenActiveClient();
        
        InstrumentResponse expected = apple();
        
        when(instrumentService.getInstrument(7)).thenReturn(expected);
        
        InstrumentResponse actual = controller.getInstrument(7, principal);
        
        assertEquals(expected, actual);
    }
    
    @Test
    void rejectsCatalogueRequestWithoutAPrincipal() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> controller.getInstruments(null));
        
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        
        verifyNoInteractions(clientService, instrumentService);
    }
    
    @Test
    void rejectsCatalogueRequestWhenClientIsNotActive() {
        when(clientService.findActiveClientIdByEmail(CLIENT_EMAIL)).thenReturn(Optional.empty());
        
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> controller.getInstruments(principal));
        
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        
        verifyNoInteractions(instrumentService);
    }
    
    @Test
    void rejectsDetailRequestWhenClientIsNotActive() {
        when(clientService.findActiveClientIdByEmail(CLIENT_EMAIL))
                .thenReturn(Optional.empty());
        
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.getInstrument(7, principal)
        );
        
        assertEquals(
                HttpStatus.UNAUTHORIZED,
                exception.getStatusCode()
        );
        
        verifyNoInteractions(instrumentService);
    }
    
    @Test
    void propagatesMissingInstrumentException() {
        givenActiveClient();
        
        NoSuchElementException expected = new NoSuchElementException("Instrument not found.");
        
        when(instrumentService.getInstrument(999)).thenThrow(expected);
        
        NoSuchElementException actual = assertThrows(NoSuchElementException.class, () -> controller.getInstrument(999, principal));
        
        assertSame(expected, actual);
    }
    
    @Test
    void propagatesInvalidInstrumentIdException() {
        givenActiveClient();
        
        IllegalArgumentException expected =
                new IllegalArgumentException(
                        "Instrument ID must be positive."
                );
        
        when(instrumentService.getInstrument(0))
                .thenThrow(expected);
        
        IllegalArgumentException actual = assertThrows(
                IllegalArgumentException.class,
                () -> controller.getInstrument(0, principal)
        );
        
        assertSame(expected, actual);
    }
    
    @Test
    void notFoundHandlerCreates404ProblemDetail() {
        NoSuchElementException exception =
                new NoSuchElementException("Instrument not found.");
        
        ProblemDetail actual = controller.handleNotFound(exception);
        
        assertAll(
                () -> assertEquals(404, actual.getStatus()),
                () -> assertEquals(
                        "Instrument not found.",
                        actual.getDetail()
                )
        );
    }
    
    @Test
    void badRequestHandlerCreates400ProblemDetail() {
        IllegalArgumentException exception =
                new IllegalArgumentException(
                        "Instrument ID must be positive."
                );
        
        ProblemDetail actual = controller.handleBadRequest(exception);
        
        assertAll(
                () -> assertEquals(400, actual.getStatus()),
                () -> assertEquals(
                        "Instrument ID must be positive.",
                        actual.getDetail()
                )
        );
    }
    
    
    
}
