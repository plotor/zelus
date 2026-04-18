package org.zhenchao.zelus.common;

import org.zhenchao.zelus.common.loader.ResourceLoader;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * Global constants
 *
 * @author zhenchao 2016-1-29 20:16:24
 */
public interface Constants {

    /** System default charset */
    Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    /** System default charset */
    String DEFAULT_ENCODING = DEFAULT_CHARSET.toString();

    /** Unified line separator, Linux style */
    String LINE_SPLITER = "\n";

    /** Connector between all attributes in a word, used for organizing output */
    String WORD_ATTRBUTE_CONNECTOR = "__";

    /** Directory name: event extraction */
    String DIR_EVENTS_EXTRACT = "event-extract";

    /** Top-level path for text results */
    String TEXT = "text";

    /** Top-level path for serialized files */
    String OBJ = "obj";

    /** Path for corpus files */
    String DIR_CORPUS = "corpus";

    /** Directory name: original text */
    String DIR_TEXT = "text";

    /** Directory name: sentence segmentation */
    String DIR_SEG_TEXT = "text_seg";

    /** Directory name: sentence segmentation (detailed) */
    String DIR_SEGDETAIL_TEXT = "text_seg-detail";

    /** Directory name: serialized word sets */
    String DIR_WORDS_OBJ = "words";

    /** Directory name: topic word vector dictionary */
    String DIR_WORDS_VECTOR = "word-vector-dict";

    /** Directory name: dependency parsing */
    String DIR_PARSE_TEXT = "text_parse";

    /** Directory name: syntactic trees */
    String DIR_SYNTACTICTREES_OBJ = "syntactic-trees";

    /** Directory name: dependency parsing */
    String DIR_PARSE_OBJ = "parse-results";

    /** Directory name: dependency parsing (simplified) */
    String DIR_PARSESIMPLIFY = "text_parse-simplify";

    /** Directory name: event extraction */
    String DIR_EVENTS = "events";

    /** Directory name: event extraction (simplified) */
    String DIR_EVENTSSIMPLIFY = "events-simplify";

    /** Directory name: coreference resolution */
    String DIR_CR_EVENTS = "events_cr";

    /** Directory name: coreference resolution (simplified) */
    String DIR_CR_EVENTSSIMPLIFY = "events_cr-simplify";

    /** Directory name: event repair */
    String DIR_CR_RP_EVENTS = "events_cr_rp";

    /** Directory name: event repair (simplified) */
    String DIR_CR_RP_EVENTSSIMPLIFY = "events_cr_rp-simplify";

    /** Directory name: phrase expansion */
    String DIR_CR_RP_PE_EVENTS = "events_cr_rp_pe";

    /** Directory name: phrase expansion (simplified) */
    String DIR_CR_RP_PE_EVENTSSIMPLIFY = "events_cr_rp_pe-simplify";

    /** Directory name: event filtering */
    String DIR_CR_RP_PE_EF_EVENTS = "events_cr_rp_pe_ef";

    /** Directory name: event filtering (simplified) */
    String DIR_CR_RP_PE_EF_EVENTSSIMPLIFY = "events_cr_rp_pe_ef-simplify";

    /** Directory name: node files */
    String DIR_NODES = "nodes";

    /** Directory name: edge files */
    String DIR_EDGES = "edges";

    /** Directory name: POS tagging */
    String DIR_TAGGED = "tagged";

    /** Directory name: event clustering */
    String DIR_EVENTS_CLUST = "events-clust";

    /** Directory name: cluster weights */
    String DIR_CLUSTER_WEIGHT = "cluster-weights";

    /** Directory name: IDF values */
    String DIR_IDF_FILE = "idf-value";

    /** Directory name: word vectors */
    String DIR_VEC_FILE = "word-vec";

    /** Directory name: sub-sentence extraction */
    String DIR_SUB_SENTENCES_EXTRACTED = "sub-sentences";

    /** Directory name: event weights */
    String DIR_EVENT_WEIGHT = "event-weights";

    /** Directory name: Chinese Whispers preprocessing */
    String DIR_CW_PRETREAT = "cw_pretreat";

    /** Directory name: multi-sentence compression */
    String DIR_SENTENCES_COMPRESSION = "compressed-results";

    /** Directory name: re-scored multi-sentence compression results */
    String DIR_RERANKED_SENTENCES_COMPRESSION = "reranked-compressed-results";

    /** Directory name: chunk phrases (simplified) */
    String DIR_CHUNKSIMPILY = "chunk-simpily";

    /** Directory name: summary results */
    String DIR_SUMMARY_RESULTS = "summary-results";

    /** Directory name: original summary results */
    String DIR_SUMMARIES_V1 = "v1";

    /** Directory name: submodular function summary results */
    String DIR_SUMMARIES_V2 = "v2";

    /** Connector between words in an event */
    String WORD_CONNECTOR_IN_EVENTS = "#";

    /** Filename marker for event location: left */
    String FILENAME_REST_LEFT = "[$";

    /** Filename marker for event location: right */
    String FILENAME_REST_RIGHT = "$]";

    /** Weight for selecting non-maximum similarity */
    int VARIATION_WEIGHT = 80;

    /**
     * Sentence count threshold: a cluster must have at least this many sentences to be compressed
     */
    int MIN_SENTENCE_COUNT_FOR_COMPRESS = 3;

    /**
     * Maximum total word count in a summary
     */
    int MAX_SUMMARY_WORDS_COUNT = 250;

    /**
     * Personal pronouns + possessive pronouns
     */
    Set<String> POS_PRP = new HashSet<String>() {
        private static final long serialVersionUID = 3536875708378397981L;

        {
            this.add("PRP");
            this.add("PRP$");
            this.add("WP");
            this.add("WP$");
        }
    };

    /**
     * Demonstrative pronoun set, to be supplemented as needed
     */
    Set<String> DEMONSTRACTIVE_PRONOUN = new HashSet<String>() {

        private static final long serialVersionUID = -1988404852361670496L;

        {
            this.add("i");
            this.add("you");
            this.add("he");
            this.add("she");
            this.add("it");
            this.add("we");
            this.add("they");
            this.add("me");
            this.add("him");
            this.add("her");
            this.add("us");
            this.add("them");
            this.add("this");
            this.add("that");
            this.add("these");
            this.add("those");
            this.add("who");
            this.add("which");
            this.add("what");
        }
    };

    /**
     * Words excluded from coreference resolution, to be supplemented as needed
     */
    Set<String> EXCEPTED_DEMONSTRACTIVE_PRONOUN = new HashSet<String>() {

        private static final long serialVersionUID = -1988404852361670496L;

        {
            this.add("i");
            this.add("you");
            this.add("he");
            this.add("she");
            this.add("it");
            this.add("we");
            this.add("they");
            this.add("me");
            this.add("him");
            this.add("her");
            this.add("us");
            this.add("them");
            this.add("its");
            this.add("this");
            this.add("that");
            this.add("these");
            this.add("those");
            this.add("my");
            this.add("your");
            this.add("his");
            this.add("their");
            this.add("who");
            this.add("which");
            this.add("what");
        }
    };

    /**
     * POS tags - nouns
     */
    Set<String> POS_NOUN = new HashSet<String>() {
        private static final long serialVersionUID = -4215344365700028825L;

        {
            this.add("NN");
            this.add("NNS");
            this.add("NNP");
            this.add("NNPS");
        }
    };

    /**
     * POS tags - verbs
     */
    Set<String> POS_VERB = new HashSet<String>() {
        private static final long serialVersionUID = 92436997464208966L;

        {
            this.add("VB");
            this.add("VBD");
            this.add("VBG");
            this.add("VBN");
            this.add("VBP");
            this.add("VBZ");
        }
    };

    /**
     * POS tags - adverbs
     */
    Set<String> POS_ADVERB = new HashSet<String>() {
        private static final long serialVersionUID = -4717718652903957444L;

        {
            this.add("RB");
            this.add("RBR");
            this.add("RBS");
        }
    };

    /**
     * POS tags - adjectives
     */
    Set<String> POS_ADJ = new HashSet<String>() {
        private static final long serialVersionUID = 1739698370056824950L;

        {
            this.add("JJ");
            this.add("JJR");
            this.add("JJS");
        }
    };

    /**
     * Dependency relations: agents (subjects)
     */
    Set<String> DEPENDENCY_AGENT = new HashSet<String>() {
        private static final long serialVersionUID = -4819853309587426759L;

        {
            this.add("nsubj");
            this.add("xsubj");
            this.add("csubj");
            this.add("agent");
        }
    };

    /**
     * Dependency relations: objects
     */
    Set<String> DEPENDENCY_OBJECT = new HashSet<String>() {
        private static final long serialVersionUID = -4819853309587426759L;

        {
            this.add("dobj");
            this.add("nsubjpass");
            this.add("acomp");
            this.add("xcomp");
        }
    };

    /** Serialized file suffix */
    String SUFFIX_SERIALIZE_FILE = ".obj";

    /** Directory name: serialized files */
    String DIR_SERIALIZE_EVENTS = "serializable-events";

    /** Word vector dimension */
    Integer DIMENSION = 300;

    /** Stopwords list */
    Set<String> STOPWORDS = ResourceLoader.loadStopwords("stopwords-en-default.txt");

    /** Excluded punctuation */
    Set<String> EXCLUDE_PUNCTUATION = new HashSet<String>() {

        private static final long serialVersionUID = 8560383953434971371L;

        {
            this.add("-LRB-");
            this.add("-RRB-");
            this.add("\"");
            this.add("\'");
            this.add(":");
        }
    };

    /** English punctuation set */
    Set<String> PUNCT_EN = new HashSet<String>() {

        private static final long serialVersionUID = -5498481342248064994L;

        {
            this.add("≠");
            this.add("≡");
            this.add("≤");
            this.add("≥");
            this.add("\"\"");
            this.add("≮");
            this.add("≯");
            this.add("＜");
            this.add("＝");
            this.add("＞");
            this.add("\"");
            this.add("#");
            this.add("△");
            this.add("!");
            this.add("&");
            this.add("'");
            this.add("...");
            this.add("%");
            this.add("*");
            this.add("+");
            this.add("≈");
            this.add("(");
            this.add(")");
            this.add("§");
            this.add(".");
            this.add("/");
            this.add(",");
            this.add("≌");
            this.add("-");
            this.add("//");
            this.add(";");
            this.add(":");
            this.add("°");
            this.add("±");
            this.add("?");
            this.add("[]");
            this.add("∠");
            this.add("⊥");
            this.add("→");
            this.add("∩");
            this.add("∪");
            this.add("∫");
            this.add("∵");
            this.add("∴");
            this.add("∷");
            this.add("℃");
            this.add("‖");
            this.add("]");
            this.add("\\");
            this.add("～");
            this.add("×");
            this.add("()");
            this.add("○");
            this.add("[");
            this.add("〃");
            this.add("⌒");
            this.add("--");
            this.add("‰");
            this.add("″");
            this.add("′");
            this.add("∑");
            this.add("⊙");
            this.add("~");
            this.add("∞");
            this.add("÷");
            this.add("∝");
            this.add("}");
            this.add("|");
            this.add("π");
            this.add("{");
            this.add("√");
        }
    };

    /** Maximum iteration count */
    int MAX_ITERATIONS = 5;

}
