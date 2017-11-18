package cs652.repl;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Stack;

/**
 * Created by vasin on 1/30/2017.
 */
public class NestedReader {
    StringBuilder buf;    // fill this as you process, character by character
    BufferedReader input; // where are we reading from?
    int c; // current character of lookahead; reset upon each getNestedString() call

    public NestedReader(BufferedReader input) {
        this.input = input;
    }

    public String getNestedString() throws IOException {
        buf = new StringBuilder();
        Stack<Character> stack = new Stack<>();
        c = input.read();
        while (true) {
            consume();
            if (c == -1) {
                return "";
            }
            switch (c) {
                case '{':
                    stack.push('}');
                    break;
                case '[':
                    stack.push(']');
                    break;
                case '(':
                    stack.push(')');
                    break;
                case '}':
                    if (!stack.empty()) {
                        Character ch = (Character) stack.pop();
                        if (!ch.equals('}')) return buf.toString();
                    }
                    break;
                case ']':
                    if (!stack.empty()) {
                        Character ch1 = (Character) stack.pop();
                        if (!ch1.equals(']')) return buf.toString();
                    }
                    break;
                case ')':
                    if (!stack.empty()) {
                        Character ch2 = (Character) stack.pop();
                        if (!ch2.equals(')')) return buf.toString();
                    }
                    break;
                case '/':
                    String buf1 = buf.toString();
                    if ((buf1 + (char)c).toString().lastIndexOf("//") > 0) {
                        while (c != '\n') {
                            consume();
                        }
                    }
                    break;
                case '\n':
                    if (stack.empty()) {
                        return buf.toString();
                    }
                    break;
                default:
                    break;
            }
        }
    }

    void consume() throws IOException {
        buf.append((char) c);
        c = input.read();
    }
}
