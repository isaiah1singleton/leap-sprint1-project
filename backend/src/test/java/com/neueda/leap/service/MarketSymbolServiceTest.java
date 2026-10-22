package com.neueda.leap.service;

import com.neueda.leap.entities.Instrument;
import com.neueda.leap.repository.InstrumentRepository;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static com.neueda.leap.support.TradingFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class MarketSymbolServiceTest {
    private final InstrumentRepository repo = mock(InstrumentRepository.class);
    private final MarketSymbolService service = new MarketSymbolService(repo);
    @Test void normalizesSearchFiltersDisabledAndRanksExactBeforePrefixBeforeName() {
        Instrument exact = instrument(), prefix = instrument(), named = instrument(), disabled = instrument();
        id(prefix, "symbol", "AAPLX"); id(named, "symbol", "ZZZ"); id(named, "instrumentName", "AAPL supplier"); disabled.disableTrading();
        when(repo.findAllByOrderByMarketAscSymbolAsc()).thenReturn(List.of(named, prefix, disabled, exact));
        assertThat(service.search(" aapl ", 100)).extracting("symbol").containsExactly("AAPL", "AAPLX", "ZZZ");
        assertThat(service.search("aapl", 1)).extracting("symbol").containsExactly("AAPL");
        assertThat(service.search(null, 1000)).hasSize(3); assertThat(service.search("missing", 100)).isEmpty();
    }
    @Test void validatesBoundsAndNormalizesLookup() {
        for (int n : new int[]{0, -1, 1001}) assertThatIllegalArgumentException().isThrownBy(() -> service.search("a", n));
        assertThatIllegalArgumentException().isThrownBy(() -> service.search("a".repeat(101), 1)); verifyNoInteractions(repo);
        when(repo.findFirstBySymbolIgnoreCase("AAPL")).thenReturn(Optional.of(instrument()));
        assertThat(service.requireSymbol(" aapl ").symbol()).isEqualTo("AAPL");
        assertThat(service.requireSymbol("AAPL").type()).isEqualTo("equity");
        assertThatThrownBy(() -> service.requireSymbol(null)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> service.requireSymbol("MISSING")).isInstanceOf(NoSuchElementException.class);
    }
}
