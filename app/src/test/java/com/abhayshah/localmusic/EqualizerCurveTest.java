package com.abhayshah.localmusic;
import org.junit.Test;
import static org.junit.Assert.*;
public class EqualizerCurveTest {
    @Test public void flatHasNoHeadroomPenalty(){assertEquals(1f,EqualizerCurve.headroom(new int[5]),0f);assertEquals(0,EqualizerCurve.at(1000,new int[5]));}
    @Test public void interpolatesAndClampsDeviceFrequencies(){int[] db={6,3,0,-3,-6};assertEquals(600,EqualizerCurve.at(0,db));assertEquals(-600,EqualizerCurve.at(30000,db));assertEquals(0,EqualizerCurve.at(910,db));assertTrue(EqualizerCurve.at(500,db)>0);}
    @Test public void boostsReserveFixedHeadroom(){assertEquals(.501187f,EqualizerCurve.headroom(new int[]{6,0,0,0,-6}),.00001f);assertEquals(1f,EqualizerCurve.headroom(new int[]{-6,-2,0,0,0}),0f);assertEquals(600,EqualizerCurve.at(60,new int[]{999,0,0,0,0}));}
}
