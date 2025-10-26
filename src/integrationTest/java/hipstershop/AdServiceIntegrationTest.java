package hipstershop;

import hipstershop.Demo.Ad;
import hipstershop.Demo.AdRequest;
import hipstershop.Demo.AdResponse;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdServiceIntegrationTest {

    private AdService.AdServiceImpl adService;
    private StreamObserver<AdResponse> mockObserver;

    @BeforeEach
    void setUp() {
        adService = new AdService.AdServiceImpl();
        mockObserver = mock(StreamObserver.class);
    }

    @Test
    void testGetAdsWithContextIntegration() {
        // Test: Request ads for "clothing" category
        AdRequest request = AdRequest.newBuilder()
                .addContextKeys("clothing")
                .build();

        adService.getAds(request, mockObserver);

        ArgumentCaptor<AdResponse> captor = ArgumentCaptor.forClass(AdResponse.class);
        verify(mockObserver, times(1)).onNext(captor.capture());
        verify(mockObserver, times(1)).onCompleted();

        AdResponse response = captor.getValue();
        assertNotNull(response);
        assertEquals(1, response.getAdsCount());
        
        Ad ad = response.getAds(0);
        assertEquals("/product/66VCHSJNUP", ad.getRedirectUrl());
        assertEquals("Tank top for sale. 20% off.", ad.getText());
    }

    @Test
    void testGetAdsMultipleCategoriesIntegration() {
        // Test: Request ads for multiple categories
        AdRequest request = AdRequest.newBuilder()
                .addContextKeys("decor")
                .addContextKeys("kitchen")
                .build();

        adService.getAds(request, mockObserver);

        ArgumentCaptor<AdResponse> captor = ArgumentCaptor.forClass(AdResponse.class);
        verify(mockObserver, times(1)).onNext(captor.capture());
        verify(mockObserver, times(1)).onCompleted();

        AdResponse response = captor.getValue();
        assertNotNull(response);
        // decor has 1 ad, kitchen has 2 ads
        assertEquals(3, response.getAdsCount());
    }

    @Test
    void testGetAdsNoCategoryIntegration() {
        // Test: Request ads with no category (should return random ads)
        AdRequest request = AdRequest.newBuilder().build();

        adService.getAds(request, mockObserver);

        ArgumentCaptor<AdResponse> captor = ArgumentCaptor.forClass(AdResponse.class);
        verify(mockObserver, times(1)).onNext(captor.capture());
        verify(mockObserver, times(1)).onCompleted();

        AdResponse response = captor.getValue();
        assertNotNull(response);
        assertEquals(2, response.getAdsCount()); // Should return 2 random ads
    }
}
