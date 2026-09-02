package com.edaxortho.marytts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PortierSpeech {

    private static final Logger LOGGER = LoggerFactory.getLogger(PortierSpeech.class);

//    private LocalMaryInterface mary = null;

    public PortierSpeech() {
//        try {
//            this.mary = new LocalMaryInterface();
//        } catch (MaryConfigurationException e) {
//            LOGGER.error("Could not initialize MaryTTS interface: {}", e.getMessage(), e);
//            throw e;
//        }
    }

    public double[] getTts(String inputText) {

//        Set<String> voices = mary.getAvailableVoices();
//        mary.setVoice(voices.iterator().next());
//
//        // synthesize
//        AudioInputStream audio = null;
//        try {
//            audio = mary.generateAudio(inputText);
//        } catch (SynthesisException e) {
//            System.err.println("Synthesis failed: " + e.getMessage());
//            System.exit(1);
//        }
//
//        // write to output
//        return MaryAudioUtils.getSamplesAsDoubleArray(audio);+
        return null;
    }
}

