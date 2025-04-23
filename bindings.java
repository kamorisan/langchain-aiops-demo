//2025/03/21時点
//DEPS dev.langchain4j:langchain4j-open-ai:0.33.0
//DEPS com.github.javafaker:javafaker:1.0.2

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.AiMessage;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

import static java.time.Duration.ofSeconds;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.github.javafaker.Faker;

public class bindings extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        // Routes are loaded from YAML files
    }

    @BindToRegistry(lazy=true)
    public static ChatLanguageModel chatModel(){

        Properties properties = new Properties();

        try (FileInputStream fis = new FileInputStream("application.properties")) {
            properties.load(fis);
        } catch (IOException e) {
            throw new RuntimeException("プロパティファイルの読み込みに失敗しました: " + e.getMessage(), e);
        }

        String apiKey = properties.getProperty("ai.apikey");
        String modelName = properties.getProperty("ai.model");
        String baseUrl = properties.getProperty("ai.baseurl");

        if (apiKey == null || modelName == null || baseUrl == null) {
            throw new IllegalStateException("環境変数が設定されていません。");
        }

        ChatLanguageModel model = OpenAiChatModel.builder()
            .apiKey(apiKey)
            .modelName(modelName)
            .baseUrl(baseUrl)
            .temperature(0.7)
            .timeout(ofSeconds(180))
            .logRequests(true)
            .logResponses(true)
            .build();

        return model;
    }

// ChatHistory
@BindToRegistry(lazy=true)
public static Processor setChatHistory(){

    return new Processor() {
        public void process(Exchange exchange) throws Exception {

            List<ChatMessage> messages = new ArrayList<>();

            // 2. Apache Camel の Body から過去のチャット履歴を取得
            // ここでは、Bodyに List<Map<String, Object>> 型でデータが格納されていると仮定
            List<Map<String, Object>> historyData = exchange.getVariable("chatHistory", List.class);
            if (historyData != null) {
                for (Map<String, Object> data : historyData) {
                    // 各フィールドを取り出し
                    String messageClass = (String) data.get("message_class");
                    String sender = (String) data.get("sender");
                    String messageBody = (String) data.get("message_body");
                    String timeStamp = data.get("time_stamp").toString();

                    // sender をメッセージ本文に追加する
                    String combinedMessageBody = "【Content】: " + messageBody 
                        + "\n【Sender】: " + sender
                        + "\n【Timestamp】: " + timeStamp;

                    // message_class に応じた ChatMessage オブジェクトを生成
                    ChatMessage message = null;
                    if ("UserMessage".equals(messageClass)) {
                        message = new UserMessage(combinedMessageBody);
                    } else if ("AiMessage".equals(messageClass)) {
                        message = new AiMessage(combinedMessageBody);
                    } else if ("SystemMessage".equals(messageClass)) {
                        message = new SystemMessage(combinedMessageBody);
                    }

                    if (message != null) {
                        messages.add(message);
                    }
                }
            }

            exchange.setVariable("chatMessage", messages);
        }
    };
}

// Communication Agent (CA)の振る舞い
    @BindToRegistry(lazy=true)
    public static Processor createPromptForCommunicationAgent(){

        return new Processor() {
            public void process(Exchange exchange) throws Exception {

                List<ChatMessage> messages = exchange.getVariable("chatMessage", List.class);

                String systemContent = """
                    【Content】: 
                    Hello. As a Communication Agent (CA), 
                    my role is to properly understand operator instructions and forward them to two specialized agents as needed.

                    My three main responsibilities are:

                    1. Operator Communication
                    * Explain technical content in an easy-to-understand manner
                    * Provide regular status reports
                    * Always obtain operator approval when important decisions are required

                    2. Forwarding to Specialized Agents
                    * Cases for forwarding to 'Intelligent Diagnostic Agent (IDA)' (Investigation & Analysis):
                    * langchain4j-tools:call-intelligent-diagnostic-agent can call
                    * When system status checks are needed
                    * When anomaly cause analysis is required
                    * When detailed log analysis is necessary
                    * Cases for forwarding to 'Smart Resolution Agent (SRA)' (Solution & Response):
                    * langchain4j-tools:call-smart-resolution-agent can call
                    * When specific solutions are needed
                    * When recovery procedure planning is required
                    * When system startup/shutdown is necessary

                    3. Information Management
                    * Record all communications and decisions
                    * Maintain constant awareness of system status
                    * Share critical information with relevant parties

                    My most important duty is to accurately understand operator instructions and route them to the appropriate agent. 
                    If anything is unclear, I will always make sure to confirm.

                    The format of the report should be a simple japanese text message only.
                    No need for 【Sender】 or 【Timestamp】.
                    """
                    + "\n【Sender】: System"
                    + "\n【Timestamp】: " + LocalDateTime.now();

                messages.add(new SystemMessage(systemContent));

                exchange.setVariable("chatMessage", messages);
                exchange.getIn().setBody(messages);
            }
        };
    }

// Intelligent Diagnostic Agent (IDA)に連携したことをユーザに通知
@BindToRegistry(lazy=true)
public static Processor callCAtoIDA(){

    return new Processor() {
        public void process(Exchange exchange) throws Exception {

            List<ChatMessage> messages = exchange.getVariable("chatMessage", List.class);

            String systemContent = """
                Hello. As a Communication Agent (CA), 
                my role is to properly understand operator instructions and forward them to two specialized agents as needed.

                Forwarding to Specialized Agents
                * Cases for forwarding to 'Intelligent Diagnostic Agent (IDA)' (Investigation & Analysis):
                * langchain4j-tools:call-intelligent-diagnostic-agent can call
                * When system status checks are needed
                * When anomaly cause analysis is required
                * When detailed log analysis is necessary

                Having just forwarded it to the Intelligent Diagnostic Agent (IDA), 
                please report that briefly to the user.
                The format of the report should be a simple japanese text message only.
                No need for 【Sender】 or 【Timestamp】.
                """
                    + "\n【Sender】: System"
                    + "\n【Timestamp】: " + LocalDateTime.now();

            messages.add(new SystemMessage(systemContent));

            exchange.setVariable("chatMessage", messages);
            exchange.getIn().setBody(messages);
        }
    };
}

// Smart Resolution Agent (SRA)に連携したことをユーザに通知
@BindToRegistry(lazy=true)
public static Processor callCAtoSRA(){

    return new Processor() {
        public void process(Exchange exchange) throws Exception {

            List<ChatMessage> messages = exchange.getVariable("chatMessage", List.class);

            String systemContent = """
                Hello. As a Communication Agent (CA), 
                my role is to properly understand operator instructions and forward them to two specialized agents as needed.

                Forwarding to Specialized Agents
                * Cases for forwarding to 'Smart Resolution Agent (SRA)' (Solution & Response):
                * langchain4j-tools:call-smart-resolution-agent can call
                * When specific solutions are needed
                * When recovery procedure planning is required
                * When system startup/shutdown is necessary

                Having just forwarded it to the Smart Resolution Agent (SRA), 
                please report that briefly to the user.
                The format of the report should be a simple japanese text message only.
                No need for 【Sender】 or 【Timestamp】.
                """
                    + "\n【Sender】: System"
                    + "\n【Timestamp】: " + LocalDateTime.now();

            messages.add(new SystemMessage(systemContent));

            exchange.setVariable("chatMessage", messages);
            exchange.getIn().setBody(messages);
        }
    };
}

// Intelligent Diagnostic Agent (IDA)の振る舞い
    @BindToRegistry(lazy=true)
    public static Processor chatWithIDA(){

        return new Processor() {
            public void process(Exchange exchange) throws Exception {

                List<ChatMessage> messages = exchange.getVariable("chatMessage", List.class);

                String systemContent = """
                    As an Intelligent Diagnostic Agent (IDA), I will operate as follows:

                    I am an agent specialized in advanced system monitoring and diagnostics. 
                    My primary purpose is to accurately identify system issues and provide detailed analysis.

                    # 1. Intelligent Diagnostic Agent (IDA) has two main tasks

                    - Perform detailed analysis of server logs and metrics (CPU, Memory Usage) data
                    - Present possible failures based on analysis of metrics and logs

                    Do not offer proposed countermeasures to the obstacle.
                    Inform the Communication Agent of the results of the analysis.

                    # 2. Get metrics (CPU, memory usage) data

                    langchain4j-tools:ida-get-container-metrics-and-logs is a function to get metrics (CPU, memory usage) data and logs data.

                    When calling a function, set “container” as a header in a List structure.

                    - application_server: "petstore-demo"
                    - database: "postgresql"

                    Output results are utilization against container resource limits.

                    # 3. Present possible failures based on analysis of metrics and logs

                    langchain4j-tools:ida-analyze-system-failures is a function to uses logs and metrics data as input to analyze the cause of system failures with a generative AI.

                    # 4. Core Principles

                    - Clearly separate diagnostic and solution roles
                    - Specialize in thorough diagnostic and investigative work
                    - Strictly adhere to agent hierarchy

                    In Communications:

                    - Prioritize technical accuracy
                    - Strive for clear and concise communication
                    - Present diagnostic results with concrete data

                    Following this protocol, I am committed to accurately diagnosing system problems and providing appropriate information. 
                    I operate purely as a diagnostic function without implementing or suggesting solutions.

                    The format of the report should be a simple japanese text message only.
                    No need for 【Sender】 or 【Timestamp】.
                    """
                        + "\n【Sender】: System"
                        + "\n【Timestamp】: " + LocalDateTime.now();

                    messages.add(new SystemMessage(systemContent));

                    exchange.setVariable("chatMessage", messages);
                    exchange.getIn().setBody(messages);
            }
        };
    }

    // Smart Resolution Agent (SRA)の振る舞い
    @BindToRegistry(lazy=true)
    public static Processor chatWithSRA(){

        return new Processor() {
            public void process(Exchange exchange) throws Exception {

                List<ChatMessage> messages = exchange.getVariable("chatMessage", List.class);

                String systemContent = """
                    As a Smart Resolution Agent (SRA), I will operate as follows:

                    I am an agent specialized in proposing and implementing solutions for system problems.
                    My primary purpose is to develop effective solutions for identified issues and manage their implementation.

                    # 1. Smart Resolution Agent (SRA) has two main tasks

                    - Propose solutions based on information from memory usage and application logs about possible causes of system failures.
                    - Implement the solution if approved by the operator

                    These tasks should not be performed simultaneously. 
                    In particular, check with the Operator through the Communication Agent (CA) before executing a solution.

                    # 2. Propose solutions for system problems.

                    langchain4j-tools:sra-presenting-generated-solution is a function to propose solutions.

                    - Regularly report all solution-related activities to Communication Agent (CA)
                    - Request additional diagnostic information through Communication Agent (CA) when needed
                    - Forward diagnostic questions to Communication Agent (CA)

                    # 3. Implement the solution if approved by the operator

                    langchain4j-tools:sra-run-playbook runs Ansible's Playbook.
                    When calling a function, set “playbook_name” as a header in a List structure.

                    "playbook_name" is (control_device)_(action)
                    (control_device) = "application_server" or "database"
                    (action) = "stop" or "start" or "reset"

                    - If operator says "Application Server and Database Reset", then run the following four playbooks.
                    "playbook_name" is [application_server_reset, database_reset]

                    But, Don't Running any playbook at the discretion of Smart Resolution Agent (SRA)
                    Always obtain approval from operator to run the Playbook.

                    Following this protocol, I focus on developing and implementing effective solutions. 
                    I remain committed to proposing and implementing solutions based on provided diagnostic information without conducting diagnostic work.

                    The format of the report should be a simple japanese text message only.
                    No need for 【Sender】 or 【Timestamp】.
                    """
                        + "\n【Sender】: System"
                        + "\n【Timestamp】: " + LocalDateTime.now();

                    messages.add(new SystemMessage(systemContent));

                    exchange.setVariable("chatMessage", messages);
                    exchange.getIn().setBody(messages);
            }
        };
    }

    // SRA_presenting_generated_solution
    @BindToRegistry(lazy=true)
    public static Processor SRA_presenting_generated_solution(){

        return new Processor() {
            public void process(Exchange exchange) throws Exception {

                List<ChatMessage> messages = exchange.getVariable("chatMessage", List.class);

                String systemContent = """
                    As a Smart Resolution Agent (SRA), I will operate as follows:

                    If there is a possible system malfunction, please provide multiple remedies to resolve it.

                    - When the phenomenon of exceeding the maximum number of concurrent users in the database occurs.
                    * Restart application server and database (Recommended)
                    * Change the maximum number of database connections

                    The format of the report should be a simple japanese text message only.
                    No need for 【Sender】 or 【Timestamp】.
                    """
                        + "\n【Sender】: System"
                        + "\n【Timestamp】: " + LocalDateTime.now();

                    messages.add(new SystemMessage(systemContent));

                    exchange.setVariable("chatMessage", messages);
                    exchange.getIn().setBody(messages);
            }
        };
    }

}

