package MetaServer.utils

/**
 * Created by orlov on 17.07.2016.
 */
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

class HDFS {
    public void save(InputStream src, String hdfsFilePath) throws IOException {
        FileSystem hdfs = makeFileSystem();
        FSDataOutputStream dest = hdfs.create(new  Path(hdfsFilePath), true);
        BufferedInputStream bis = new BufferedInputStream(src);
        int br;
        while ((br = bis.read()) > 0) {
            dest.write(br);
        }
        dest.hflush();
    }

    public void copyToHdfs(String localFileName, String hdfsFileName) throws IOException {
        FileSystem hdfs = makeFileSystem();
        hdfs.copyFromLocalFile(false, true, new Path(localFileName), new Path(hdfsFileName));
    }

    public String read(String hdfsFileName) throws IOException {
        FileSystem hdfs = makeFileSystem();
        if (!hdfs.exists(new Path(hdfsFileName))) {
            return "";
        }

        BufferedReader br = new BufferedReader(new InputStreamReader(hdfs.open(new Path(hdfsFileName))));
        StringBuilder sb = new StringBuilder();
        String line = br.readLine();
        while (line != null) {
            sb.append(line).append("\n");
            line = br.readLine();
        }

        return sb.toString();
    }

    private FileSystem makeFileSystem() throws IOException {
        Configuration config = new Configuration();

        String hadoopConfDir = System.getenv("HADOOP_CONF_DIR");
        if (!isNullOrEmpty(hadoopConfDir)) {
            config.addResource(new Path(hadoopConfDir + "/core-site.xml"));
            config.addResource(new Path(hadoopConfDir + "/hdfs-site.xml"));
        } else {
            ClassLoader classloader = Thread.currentThread().getContextClassLoader();
            config.addResource(classloader.getResourceAsStream("config/core-site.xml"));
            config.addResource(classloader.getResourceAsStream("config/hdfs-site.xml"));
        }

        return FileSystem.get(config);
    }
}
