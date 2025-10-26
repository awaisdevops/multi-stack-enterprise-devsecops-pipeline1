package hipstershop;

import hipstershop.Demo.Ad;
import hipstershop.Demo.AdRequest;
import hipstershop.Demo.AdResponse;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class AdServiceTest {

    private AdService.AdServiceImpl adServiceImpl;
    private StreamObserver<AdResponse> streamObserver;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        adServiceImpl = new AdService.AdServiceImpl();
        streamObserver = (StreamObserver<AdResponse>) mock(StreamObserver.class);
    }

    @Test
    void getAds_noContext_returnsRandomAds() {
        AdRequest request = AdRequest.newBuilder().build();
        adServiceImpl.getAds(request, streamObserver);

        ArgumentCaptor<AdResponse> responseCaptor = ArgumentCaptor.forClass(AdResponse.class);
        verify(streamObserver, times(1)).onNext(responseCaptor.capture());
        verify(streamObserver, times(1)).onCompleted();
        verify(streamObserver, never()).onError(any(Throwable.class));

        AdResponse response = responseCaptor.getValue();
        // The service is hardcoded to return 2 random ads when no context is given
        assertEquals(2, response.getAdsCount());
    }

    @Test
    void getAds_withContext_returnsCategoricalAds() {
        // "decor" is one of the categories with a single, known ad
        AdRequest request = AdRequest.newBuilder().addContextKeys("decor").build();
        adServiceImpl.getAds(request, streamObserver);

        ArgumentCaptor<AdResponse> responseCaptor = ArgumentCaptor.forClass(AdResponse.class);
        verify(streamObserver, times(1)).onNext(responseCaptor.capture());
        verify(streamObserver, times(1)).onCompleted();
        
        AdResponse response = responseCaptor.getValue();
        assertEquals(1, response.getAdsCount());
        Ad ad = response.getAds(0);
        assertEquals("/product/0PUK6V6EV0", ad.getRedirectUrl());
        assertEquals("Candle holder for sale. 30% off.", ad.getText());
    }
}
