package com.abhayshah.localmusic;
import org.junit.Test;import static org.junit.Assert.*;
public class VolumeEnvelopeTest {
    double settledRms(double amplitude){VolumeEnvelope level=new VolumeEnvelope(8000);double energy=0;int count=0;short[] frame=new short[2];for(int i=0;i<8000*10;i++){short sample=(short)(Math.sin(i*2*Math.PI*440/8000)*32767*amplitude);frame[0]=sample;frame[1]=sample;level.process(frame);assertEquals(frame[0],frame[1]);if(i>8000*8){energy+=Math.pow(frame[0]/32768d,2);count++;}}return Math.sqrt(energy/count);}
    @Test public void quietAndLoudTracksApproachSimilarLevels(){assertEquals(settledRms(.2),settledRms(.7),.015);}
    @Test public void peaksStayBelowCeilingAndChannelsStayLinked(){VolumeEnvelope level=new VolumeEnvelope(48000);short[] frame={(short)32767,(short)-16384};level.process(frame);assertTrue(Math.abs(frame[0])<=29165);assertEquals(-2d,frame[0]/(double)frame[1],.001);}
    @Test public void silenceStaysSilent(){VolumeEnvelope level=new VolumeEnvelope(48000);short[] frame={0,0};for(int i=0;i<1000;i++)level.process(frame);assertArrayEquals(new short[]{0,0},frame);}
}
