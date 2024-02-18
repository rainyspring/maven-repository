package xu.jiang.bili.bili.m4s;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.ArrayUtils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

/**
 * 将手机版 bili 的缓存视频目录所有视频 -> 多个 mp4
 * 
 * bili 简写为 BL
 * 
 * @author we749
 *
 */
public class BiliMerge4Rain {

//	private static List<BLFileEntity> fileList = new ArrayList<>();

	private static Map<MapKey, BLFileEntity> fileMap = new HashMap<>();

	public static void main(String[] args) throws IOException {
		// bilibili视频缓存所在的文件夹,自动递归查找文件夹
		Path inFile = Paths.get("F:\\xiaoyun6");
		// ffmpeg.exe 的所在目录
		File ffmpegFile = new File("D:\\log\\ffmpeg\\bin\\ffmpeg.exe");
		// 输出目录
		Path distDir = Paths.get("F:\\xiaoyun6\\movie\\");

		long start = System.currentTimeMillis();

		/**
		 * 收集所有文件的名字
		 */
		findAllfiles(inFile.toFile(), distDir);

		fileMap.values().stream().parallel()
				.filter(o -> !o.getOutFile().isFile())
				.forEach(o2 -> {
					
					if (o2.getAudioFile() != null && o2.getVideoFile() != null) {
						ffmpegUtil(o2.getVideoFile(), o2.getAudioFile(),o2.getOutFile(), ffmpegFile);
						
						System.out.println(o2.getName() + ",转换成功");
					
					} else {
						if (null == o2.getVideoFile()) {
							System.err
									.println("编号id为：" + o2.getId() + " 输入的video.m4s 不存在" + ".详细信息为： " + o2);
						}
						if (null == o2.getAudioFile()) {
							System.err
									.println("编号id为：" + o2.getId() + " 输入的audio.m4s 不存在" + ".详细信息为： " + o2);
						}
					}
				});

		System.out.println("所用时间：" + (System.currentTimeMillis() - start));

	}


	/**
	 * becouse all cache files have unreadable name ,mainly they are located in
	 * diferent places.
	 * 
	 * @param file
	 * @throws IOException
	 */
	public static void findAllfiles(File file, Path distDir) throws IOException {

		if (file.isDirectory()) {
			String[] children = file.list();
			if (ArrayUtils.isEmpty(children)) {
				return;
			}

			for (String child : children) {
				findAllfiles(new File(file, child), distDir);
			}
			return;
		}
		if (!file.isFile()) {
			return;
		}

		String fileName = file.getName();

		if (!"entry.json".equals(fileName) && !"audio.m4s".equals(fileName) && !"video.m4s".equals(fileName)) {
			return;
		}

		/**
		 * 手机缓存里一个视频文档 包含三个文件：entry.json、audio.m4s、video.m4s 目录结构如下： 
		 * (目录 avid)[ 
		 * 		(目录 id)[
		 * 			entry.json, 
		 * 			(目录名忘了)[ 
		 * 				audio.m4s, 
		 * 				video.m4s,
		 * 				(...其他文件略) 
		 * 			] ,
		 * 			(...其他文件略) 
		 * 		]
		 * (其他目录略)
		 * ]
		 * 
		 * 这里有个关联，即 目录中的 avid 在 entry.json 中 也存在记录
		 */
		if ("entry.json".equals(fileName)) {
			String fileId = file.getParentFile().getName();
			
			String jsonStr = new String(Files.readAllBytes(file.toPath()));
			Map<String, Object> map = JSON.parseObject(jsonStr, Map.class);

			String title = (String) map.get("title");
			String avid = (String) map.get("avid");
			JSONObject page_data = (JSONObject) map.get("page_data");
			String download_subtitle = (String) page_data.get("download_subtitle");

			BLFileEntity o = getOrCreateBLFileEntity(MapKey.getKey(fileId, avid),distDir);
			if (o != null) {
				o.setParentName(title);
				o.setName(download_subtitle);
				o.setAvid(avid);
			}
			return;
		}

		/**
		 * 这个目录名 是视频文档唯一标识： id
		 */
		File parentFile = file.getParentFile().getParentFile();

		// 获取 avId
		String avId = parentFile.getParentFile().getName();
		String fileId = parentFile.getName();
		MapKey key = new MapKey(fileId, avId);
		BLFileEntity o = getOrCreateBLFileEntity(key,distDir);
		

		if ("audio.m4s".equals(fileName)) {
			o.setAudioFile(file);

		}

		else if ("video.m4s".equals(fileName)) {
			o.setVideoFile(file);
		}
		
		
	}

	/**
	 * 
	 * @param id
	 * @param avId
	 * @return
	 */
	private static BLFileEntity getOrCreateBLFileEntity(MapKey key,Path distDir) {
		// 需要检查是否已经存在
		BLFileEntity o = fileMap.get(key);
		if (o != null) {
			return o;
		}

		// 如果不存在，就需要创建
		o = new BLFileEntity();
		o.setId(key.fileId);
		o.setAvid(key.avId);
		o.setOutDir(distDir);
		fileMap.put(key, o);
		return o;
	}


	/**
	 * make video type file and audio type file of the document to one file of mp4
	 * 
	 * @param videoFile
	 * @param audioFile
	 * @param outFile
	 * @param FFmpeg
	 */
	private static void ffmpegUtil(File videoFile, File audioFile, File outFile, File FFmpeg) {
		if (videoFile == null || audioFile == null || outFile == null || FFmpeg == null) {
			return;
		}
		ProcessBuilder processBuilder = new ProcessBuilder();
		List<String> command = new ArrayList<>();
		command.add(FFmpeg.toString());
		command.add("-i");
		command.add(videoFile.toString());
		command.add("-i");
		command.add(audioFile.toString());
		command.add("-codec");
		command.add("copy");
		command.add(outFile.toString());
		processBuilder.command(command);
		processBuilder.redirectErrorStream(true);

//            InputStream inputStream = null;
		Process process = null;
		try {
			// 启动进程
			process = processBuilder.start();
//                inputStream = process.getInputStream();
			// 转成字符流
//                List list = IOUtils.readLines(inputStream, "utf-8");
//                list.stream().forEach(System.out::println);
		} catch (IOException e) {
			e.printStackTrace();
//            } finally {
//                IOUtils.closeQuietly(inputStream);
		}
	}

	private static class BLFileEntity {
		private String id;
		private String parentName;
		private String name;

		private File videoFile;
		private File audioFile;

		private Path outDir;

		/**
		 * @note 类似分组的意思
		 */
		private String avid;
		
		
		/**
		 * 获取输出文件
		 * @return
		 */
		public File getOutFile() {
			
			return this.getOutDir().resolve(this.getName()).resolve(".mp4").toFile();
		}

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public String getParentName() {
			return parentName;
		}

		public void setParentName(String parentName) {
			this.parentName = parentName;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getAvid() {
			return avid;
		}

		public void setAvid(String avid) {
			this.avid = avid;
		}

		public File getVideoFile() {
			return videoFile;
		}

		public void setVideoFile(File videoFile) {
			this.videoFile = videoFile;
		}

		public File getAudioFile() {
			return audioFile;
		}

		public void setAudioFile(File audioFile) {
			this.audioFile = audioFile;
		}

		public Path getOutDir() {
			return outDir;
		}

		public void setOutDir(Path outDir) {
			this.outDir = outDir;
		}

	
		

	}

	public static class MapKey {
		private String fileId;
		private String avId;

		private MapKey(String fileId, String avId) {

			this.fileId = fileId;
			this.avId = avId;
		}

		public static MapKey getKey(String fileId, String avId) {
			return new MapKey(fileId, avId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(avId, fileId);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			MapKey other = (MapKey) obj;
			return Objects.equals(avId, other.avId) && Objects.equals(fileId, other.fileId);
		}

	}
}
