package org.dataalgorithms.chapB12.docwordcount.spark;

import scala.Tuple2;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.api.java.function.Function2;
import org.apache.spark.api.java.function.PairFunction;
import org.apache.spark.api.java.function.FlatMapFunction;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Description:
 *
 *    DocumentWordCount: Counting the words per document. 
 *                       Keep only words if word size is greater than 2.
 *                       Words like "an" and "of" will be dropped.
 *
 * Building Basic Search Index:
 * ============================
 * The goal of this assignment is create a basic index to be used
 * for searching keywords.
 *
 *
 * Input format:
 * =============
 * <document-ID><:><word-1><,><word-2><,><word-3>...
 * 
 * Sample Input 
 * ============
doc6:of,crazy,fox,jumped,fox,ran
doc6:fox,ran,fast,over,fence,too,high
doc1:fox,jumped
doc1:fox,jumped,over,the,fence
doc1:of,crazy,fox,jumped,fox,ran
doc1:fox,ran,fast,over,fence,too,high
doc2:a,crazy,fox,jumped
doc2:a,crazy,fox,jumped,over,the,fence,again
doc3:fox,ran,fast,over,fence
doc1:fox,is,high,on,sugar
doc2:a,crazy,fox,ran,ran,ran,fast
doc3:a,crazy,fox,jumped,jumped,jumped,jumped
doc4:a,crazy,fox,ran,jumped,ran,jumped
doc4:a,crazy,fox,jumped,jumped
doc4:a,crazy,fox,jumped,jumped
doc5:a,crazy,fox,jumped,over,fence,very,high
doc5:a,crazy,fox,jumped,over,the,fence,again
doc6:book,reading,about,fox,and,fence
doc1:crazy,fox,jumped

Expected output:
================
 <each-unique-word><frequency_1,doc_1><frequency_2,doc_2><frequency_3,doc_3>...

 where 
    frequency_1 > frequency_2 > frequency_3 > ...


 * @author Mahmoud Parsian
 *
 */
public class DocumentWordCount {

    static List<Tuple2<Integer,String>> iterableToList(Iterable<Tuple2<Integer,String>> iterable) {
       List<Tuple2<Integer,String>> list = new ArrayList<Tuple2<Integer,String>>();
       for (Tuple2<Integer,String> item : iterable) {
          list.add(item);
       }
       return list;
    }    
    
    public static void main(String[] args) throws Exception {
       if (args.length != 3) {
          System.err.println("Usage: DocumentWordCount <N> <input> <output>");
          System.exit(1);
       }

       // handle input parameters
       final int N = Integer.parseInt(args[0]);
       final String inputPath = args[1];
       final String outputPath = args[2];

       // create a context object, which is used 
       // as a factory for creating new RDDs
       JavaSparkContext ctx = new JavaSparkContext();

       // read input and create the first RDD
       JavaRDD<String> lines = ctx.textFile(inputPath, 1);

       JavaRDD<Tuple2<String,String>> wordsAndDocs = 
          lines.flatMap(new FlatMapFunction<
                                            String,                 // input
                                            Tuple2<String,String>   // output
                                           >() {
          //              output                      input
          public Iterable<Tuple2<String,String>> call(String s) {
             if ((s == null) || (s.length() < N)) {
                return Collections.emptyList();
             }
             
             String[] tokens = s.split(":");
             String documentID = tokens[0];
             String wordsAsString = tokens[1];
             String[] words = wordsAsString.split(",");
             List<Tuple2<String,String>> list = new ArrayList<Tuple2<String,String>>();
             for (String  word : words) {
                if (word.matches(".*[,.;]$")) {
                   // remove the special char from the end
                   word = word.substring(0, word.length() -1); 
                }
           
                if (word.length() < N) {
                   continue;
                }
           
                list.add(new Tuple2<String,String>(word, documentID));
             }
             return list;
          }
       });

        //         K                      V    
       JavaPairRDD<Tuple2<String,String>, Integer> ones = 
         wordsAndDocs.mapToPair(new PairFunction<
                                                 Tuple2<String,String>, // input
                                                 Tuple2<String,String>, // K
                                                 Integer                // V
                                                >() {
         //            K                      V             input
         public Tuple2<Tuple2<String,String>, Integer> call(Tuple2<String,String> s) {
           //                K                      V
           return new Tuple2<Tuple2<String,String>, Integer>(s, 1);
         }
       });

       // find the total count for each unique word per documentID
       JavaPairRDD<Tuple2<String,String>, Integer> counts = 
            ones.reduceByKey(new Function2<Integer, Integer, Integer>() {
          public Integer call(Integer i1, Integer i2) {
             return i1 + i2;
          }
       });
       
       // counts: { [(word1, doc1), f1], [(word2, doc2), f2], ... }
       // create another RDD as:
       // wordAsKey: { [word1, (f1, doc1)], [word2, (f2, doc2)], ...]
       //          K       V    
       JavaPairRDD<String, Tuple2<Integer,String>> wordAsKey = 
         counts.mapToPair(new PairFunction<
                                           Tuple2<Tuple2<String,String>,Integer>, // input
                                           String,                                // K
                                           Tuple2<Integer,String>                 // V
                                          >() {
         //            K      V                            input
         public Tuple2<String,Tuple2<Integer,String>> call(Tuple2<Tuple2<String,String>,Integer>  s) {
           String word = s._1._1;
           String documntID = s._1._2;
           Integer frequency = s._2;
           Tuple2<Integer, String> freqAndDocument = new Tuple2<Integer, String>(frequency, documntID);
           return new Tuple2<String,Tuple2<Integer,String>>(word, freqAndDocument);
         }
       });

       // now group words and sort by their frequencies
       JavaPairRDD<String, Iterable<Tuple2<Integer,String>>> groupedByWord = wordAsKey.groupByKey();
       //JavaPairRDD<String, Iterable<Tuple2<Integer,String>>> sorted = groupedByWord.groupByKey();
   
       // Sort the reducer's values and this will give us the final output.
       // mapValues[U](f: (V) ⇒ U): JavaPairRDD[K, U]
       // Pass each value in the key-value pair RDD through a map function without changing the keys;
       // this also retains the original RDD's partitioning.
       JavaPairRDD<String, Iterable<Tuple2<Integer, String>>> sorted = 
          groupedByWord.mapValues(new Function<
                                               Iterable<Tuple2<Integer, String>>,      // input
                                               Iterable<Tuple2<Integer, String>>       // output
                                              >() {
          public Iterable<Tuple2<Integer, String>> call(Iterable<Tuple2<Integer, String>> s) {
             List<Tuple2<Integer, String>> list = new ArrayList<Tuple2<Integer, String>>(iterableToList(s));
             Collections.sort(list, FrequencyComparator.INSTANCE);
             return list;
          }
       });
    

       // save the final output 
       sorted.saveAsTextFile(outputPath);

       // close the context and we are done
       ctx.close();
       System.exit(0);
    }
    
}

