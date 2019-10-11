
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.swrlapi.core.SWRLAPIRule;
import org.swrlapi.core.SWRLRuleEngine;
import org.swrlapi.factory.SWRLAPIFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.UUID;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;

public class hermit {

    static private String getGUID() {
        UUID uuid = UUID.randomUUID();
        return String.valueOf(uuid).toUpperCase();
    }

    public static void main(String[] args) {

        String folder = "";

        try {


            String ruleURL = "https://lassoing-rhetoric-shared.s3-eu-west-1.amazonaws.com/lassoing-rule.txt";

            URL url = new URL(ruleURL);
            HttpURLConnection connection = (HttpURLConnection)url.openConnection();
            connection.setRequestMethod("GET");
            connection.connect();

            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String inputLine;
            StringBuffer response = new StringBuffer();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            String[] rules = response.toString().split(";");

            String localBucket = "/Users/clifforeilly/Downloads/localBucket";
            String bucket_name = "lassoing-rhetoric-p4-onto";
            System.out.format("Objects in S3 bucket %s:\n", bucket_name);
            final AmazonS3 s3 = AmazonS3ClientBuilder.standard().withRegion(Regions.EU_WEST_1).build();
            ListObjectsV2Result result = s3.listObjectsV2(bucket_name);
            List<S3ObjectSummary> objects = result.getObjectSummaries();
            for (S3ObjectSummary os : objects) {
                System.out.println("* " + os.getKey());
                folder = os.getKey().split("/")[0];

                File directory = new File(localBucket + "/inputs/" + folder);
                if (! directory.exists()){
                    directory.mkdir();
                }

                try {
                    S3Object o = s3.getObject(bucket_name, os.getKey());
                    S3ObjectInputStream s3is = o.getObjectContent();
                    File file = new File(localBucket + "/inputs/" + os.getKey());
                    if (!file.exists()) {
                        file.createNewFile();
                    }
                    FileOutputStream fos = new FileOutputStream(new File(localBucket + "/inputs/" + os.getKey()));
                    byte[] read_buf = new byte[1024];
                    int read_len = 0;
                    while ((read_len = s3is.read(read_buf)) > 0) {
                        fos.write(read_buf, 0, read_len);
                    }
                    s3is.close();
                    fos.close();
                } catch (AmazonServiceException e) {
                    System.err.println(e.getErrorMessage());
                    System.exit(1);
                } catch (FileNotFoundException e) {
                    System.err.println(e.getMessage());
                    System.exit(1);
                } catch (IOException e) {
                    System.err.println(e.getMessage());
                    System.exit(1);

                }
            }

            File folderInput = new File(localBucket + "/inputs/" + folder);

            for (final File fileEntry : folderInput.listFiles()) {
                System.out.println(fileEntry.getName());
                if(!fileEntry.getName().contains(".DS_Store")) {

                    OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
                    File inputOntologyFile = new File(localBucket + "/inputs/" + folder + "/" + fileEntry.getName());
                    OWLOntology ontology = manager.loadOntologyFromOntologyDocument(inputOntologyFile);
                    SWRLRuleEngine ruleEngine = SWRLAPIFactory.createSWRLRuleEngine(ontology);

                    for (String rule:rules) {
                        System.out.println(rule.replace(";",""));
                        SWRLAPIRule ruleX = ruleEngine.createSWRLRule("Test-Rule-1", rule.replace(";",""));
                        //ruleEngine.getAssertedOWLAxioms();
                        ruleEngine.infer();
                        ruleEngine.run();
                        for (OWLAxiom axi : ruleEngine.getAssertedOWLAxioms()) {
                            String assrtty = axi.toString();
                            if (assrtty.contains("Litotes") && assrtty.contains("Word") && !assrtty.contains("DLSafeRule")) {
                                System.out.println("===============" + assrtty);
                            }
                        }
                    }
                    manager.saveOntology(ontology, IRI.create(new File(localBucket + "/infer/" + fileEntry.getName()).toURI()));
                }
            }

        }
        catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
}
