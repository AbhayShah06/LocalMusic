package com.abhayshah.localmusic;

/** Linked-channel RMS leveling with gentle gain changes and a peak ceiling. */
final class VolumeEnvelope {
    private static final double TARGET=.14,CEILING=.89;
    private final double powerRate,attack,release;private double power=TARGET*TARGET,gain=1;
    VolumeEnvelope(int sampleRate){powerRate=1-Math.exp(-1d/(sampleRate*1.5));attack=1-Math.exp(-1d/(sampleRate*.08));release=1-Math.exp(-1d/(sampleRate*1.5));}
    void process(short[] frame){
        double energy=0,peak=0;for(short sample:frame){double value=sample/32768d;energy+=value*value;peak=Math.max(peak,Math.abs(value));}
        power+=(energy/frame.length-power)*powerRate;double rms=Math.sqrt(Math.max(0,power));
        double wanted=rms<.008?1:Math.min(2,TARGET/Math.max(.001,rms));gain+=(wanted-gain)*(wanted<gain?attack:release);
        double applied=Math.min(gain,peak>0?CEILING/peak:gain);
        for(int i=0;i<frame.length;i++)frame[i]=(short)Math.round(Math.max(-32768,Math.min(32767,frame[i]*applied)));
    }
}
