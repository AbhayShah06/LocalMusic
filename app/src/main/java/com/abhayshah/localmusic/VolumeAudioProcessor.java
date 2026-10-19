package com.abhayshah.localmusic;
import androidx.media3.common.C;
import androidx.media3.common.audio.*;
import java.nio.*;
import java.util.function.BooleanSupplier;

@androidx.media3.common.util.UnstableApi
final class VolumeAudioProcessor extends BaseAudioProcessor {
    private final BooleanSupplier enabled;private VolumeEnvelope envelope;private short[] frame;private byte[] partial;private int partialCount;
    VolumeAudioProcessor(BooleanSupplier enabled){this.enabled=enabled;}
    @Override protected AudioFormat onConfigure(AudioFormat format)throws UnhandledAudioFormatException{
        if(format.encoding!=C.ENCODING_PCM_16BIT)throw new UnhandledAudioFormatException(format);
        return format;
    }
    @Override protected void onFlush(){partialCount=0;if(inputAudioFormat.sampleRate<=0||inputAudioFormat.channelCount<=0){frame=new short[0];partial=new byte[0];return;}envelope=new VolumeEnvelope(inputAudioFormat.sampleRate);frame=new short[inputAudioFormat.channelCount];partial=new byte[frame.length*2];}
    @Override public void queueInput(ByteBuffer input){
        int frameBytes=partial.length;if(frameBytes==0){input.position(input.limit());return;}
        ByteBuffer output=replaceOutputBuffer(((partialCount+input.remaining())/frameBytes)*frameBytes).order(ByteOrder.nativeOrder());boolean level=enabled.getAsBoolean();
        while(input.hasRemaining()){
            partial[partialCount++]=input.get();if(partialCount<frameBytes)continue;
            for(int i=0;i<frame.length;i++)frame[i]=(short)((partial[i*2]&255)|(partial[i*2+1]<<8));
            if(level)envelope.process(frame);for(short sample:frame)output.putShort(sample);partialCount=0;
        }
        output.flip();
    }
}
