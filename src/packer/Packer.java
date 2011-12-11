package packer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Rice Hi-Res texture packer for Wii64
 * 
 * @author emu_kidid
 */

public class Packer {

	private static Archiver	archiver	= null;

	public static void main(String[] argv) {
		try {
			BufferedWriter logFile = new BufferedWriter(new FileWriter(new File("." + "\\logfile.txt")));
			archiver = new Archiver(logFile);
			archiver.setVisible(true);
			while (archiver.isVisible()) {
				Thread.sleep(100);
				if (archiver.isStartPushed()) {
					archiver.run();
					archiver.setStartPushed(false);
				}
			}
		} catch (IOException e) {
			System.out.println("Error Creating output archive to: " + archiver.getPakPath());
		} catch (InterruptedException e) {
		}
	}

}
