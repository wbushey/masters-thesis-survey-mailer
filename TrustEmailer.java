import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.Properties;
import java.util.Scanner;
import java.util.StringTokenizer;
import java.util.Vector;

import javax.activation.DataHandler;
import javax.mail.*;
import javax.mail.internet.*;
import javax.mail.util.ByteArrayDataSource;

import java.util.*;
import java.io.*;


public class TrustEmailer {
	
	// Read stuff from cmd
	public Scanner scanner = new Scanner(System.in);
	
	// The Database Objects
	private Connection connect = null;
	private Statement statement = null;
	private PreparedStatement preparedStatement = null;
	private ResultSet rs = null;
	
	// Survey links
	private String[] surveyAddresses = {"", "https://www.surveymonkey.com/s/6L9N82B", "https://www.surveymonkey.com/s/6NGKMNS", "https://www.surveymonkey.com/s/6F87F2R", "https://www.surveymonkey.com/s/6NH6LGT"};
	private static final int SURVEY1 = 1;
	private static final int SURVEY2 = 2;
	private static final int SURVEY3 = 3;
	private static final int SURVEY4 = 4;
	
	


	public TrustEmailer() {
		// TODO Auto-generated constructor stub
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception{
		TrustEmailer te = new TrustEmailer();
		
		int response = 0;
		
		// Run the menu
		while (response < 7){
			// Present the text of the menu
			System.out.println();
			System.out.println("##############################################");
			System.out.println("Privacy Trust Emailer");
			System.out.println("Please Choose One Of The Following:");
			System.out.println("\t 1 - Import Emails");
			System.out.println("\t 2 - Create Sample Frame");
			System.out.println("\t 3 - Import Respondent IDs");
			System.out.println("\t 4 - Send Emails");
			System.out.println("\t 5 - Pick Random Respondent");
			System.out.println("\t 6 - View Email Log");
			System.out.println("\t 7 - Exit");
			System.out.println("(1-7):");
			
			// Get the response
			response = Integer.parseInt(te.scanner.nextLine());
			
			// Branch on response
			switch (response){
			case 1:
				te.importEmails();
				break;
			case 2:
				te.createSampleFrame();
				break;
			case 3:
				te.importRespondentIDs();
				break;
			case 4:
				te.sendEmailsMenu();
				break;
			case 5:
				te.pickRandomRespondent();
				break;
			case 6:
				te.viewEmailLog();
				break;
			case 7:
				System.out.println("Exiting");
				break;
			default:
				System.out.println("Improper Response.");
				break;
			}
		}
	}
	
	/**
	 * Asks the user for a filename, reads the file, then writes the emails to the database.
	 */
	public void importEmails() throws Exception{
		String fn;
		Vector<String> emails = null;
		
		try{
			// Get the filename
			System.out.println("Enter the spreadsheet filename:");
			fn = scanner.nextLine();
			emails = getColumn(fn, 0, 1);
			Enumeration<String> emailEnum = emails.elements();
			
			// Add emails to the database
			open();
			preparedStatement = connect.prepareStatement("insert into graduate.email (email) values (?)");
			while (emailEnum.hasMoreElements()){
				preparedStatement.setString(1, emailEnum.nextElement());
				try {
					preparedStatement.executeUpdate();
				} catch (SQLException mysqlE){
					if (mysqlE.getErrorCode() == 1062){
						System.out.println("Duplicate Email Not Added");
					} else throw mysqlE;
				}
			}
		} catch (FileNotFoundException e){
			System.out.println("The filename you entered could not be found.");
		} catch (Exception e){
			throw e;
		} finally {
			close();
		}
	}
	
	/**
	 * Asks the user for a survey number and a frame size, then creates a frame for that survey from available email addresses.
	 * @throws Exception 
	 */
	public void createSampleFrame() throws Exception{
		// Ask some questions
		System.out.println("Surveys:");
		System.out.println("\t 1 - Voluntary Self-Summary");
		System.out.println("\t 2 - Audited Self-Summary");
		System.out.println("\t 3 - Voluntary 3rd Party Summary");
		System.out.println("\t 4 - Audited 3rd Party Summary");
		System.out.println("For Which Survey Do You Want To Make A Frame?");
		int surveyNum = Integer.parseInt(scanner.nextLine());
		System.out.println("How Large Do You Want The New Frame To Be?");
		int frameSize = Integer.parseInt(scanner.nextLine());
		
		int addedToFrame = 0;
		
		try {
			open();
			
			// First, decide the new frame number
			int frameNum;
			rs = statement.executeQuery("select distinct frameNum from email where surveyNum = " + surveyNum + " order by frameNum desc limit 1");
			if (rs.next()){
				frameNum = rs.getInt(1) + 1;
			} else {
				frameNum = 1;
			}
			
			// Get the random results
			rs = statement.executeQuery("select participantID from email where surveyNum is null order by rand() limit " + frameSize);
			
			// Set the random results to be in the new survey frame
			preparedStatement = connect.prepareStatement("update email set surveyNum = ?, frameNum = ?, hasResponded = 0 where participantID = ?");
			preparedStatement.setInt(1, surveyNum);
			preparedStatement.setInt(2, frameNum);
			while(rs.next()){
				preparedStatement.setInt(3, rs.getInt(1));
				preparedStatement.executeUpdate();
				addedToFrame++;
			}
			
			System.out.println("Frame # " + frameNum + " created for survey # " + surveyNum + " with " + addedToFrame + " addresses.");
		} catch (Exception e){
			throw e;
		} finally {
			close();
		}
	}
	
	/**
	 * Asks the user for the filename that contains respondent IDs, then updates those IDs indicated that they have responded
	 * @throws Exception 
	 */
	public void importRespondentIDs() throws Exception{
		String fn;
		Vector<String> ids = null;
		
		try{
			// Get the filename
			System.out.println("Enter the spreadsheet filename:");
			fn = scanner.nextLine();
			ids = getColumn(fn, 8, 2);	// Custom Date is column 9 on the All Responses Collected/Condensed Columns/Numerical Value (1-n) selection
			Enumeration<String> idEnum = ids.elements();
			
			// Build the update statement
			String stmt = "update email set hasResponded=1 where participantID in(";
			while (idEnum.hasMoreElements()){
				stmt = stmt.concat(idEnum.nextElement());
				if (idEnum.hasMoreElements()) stmt = stmt + ",";
			}
			stmt = stmt.concat(")");
			
			// Execute the update
			open();
			statement.execute(stmt);
			System.out.println("Respondents Successfully Imported");
			
		} catch (FileNotFoundException e){
			System.out.println("The filename you entered could not be found.");
		} catch (Exception e){
			throw e;
		} finally {
			close();
		}
	}
	
	/**
	 * @throws Exception 
	 * 
	 */
	public void sendEmailsMenu() throws Exception{
		
		// Ask some questions
		System.out.println("Surveys:");
		System.out.println("\t 0 - All");
		System.out.println("\t 1 - Voluntary Self-Summary");
		System.out.println("\t 2 - Audited Self-Summary");
		System.out.println("\t 3 - Voluntary 3rd Party Summary");
		System.out.println("\t 4 - Audited 3rd Party Summary");
		System.out.println("For Which Survey Do You Want To Send Emails?");
		int surveyNum = Integer.parseInt(scanner.nextLine());
		System.out.println("Which Frame Would You Like To Email?");
		int frameNum = Integer.parseInt(scanner.nextLine());
		
		if (surveyNum == 0){
			for (int i = 1; i <= 4; i++){
				sendEmails(i, frameNum);
			}
		} else {
			sendEmails(surveyNum, frameNum);
		}
	}
	
	
	
	/**
	 * @throws Exception 
	 * 
	 */
	public void pickRandomRespondent() throws Exception{
		System.out.println();
		try{
			open();
			rs = statement.executeQuery("select email from email where hasResponded = 1 order by rand() limit 1");
			if (rs.next()){
				System.out.println("The winning respondent is: " + rs.getString(1));
			} else {
				throw new Exception("Selecting a random respondent did not return any results");
			}
		} catch (Exception e){
			throw e;
		} finally {
			close();
		}
		
	}

	/**
	 * @throws Exception 
	 * Prints a log of all previous email sessions
	 */
	public void viewEmailLog() throws Exception{
		System.out.println("Start Time \t| End Time \t| Survey Number \t| Frame Number \t| Number of Emails Sent");
		try{
			open();
			rs = statement.executeQuery("select * from emailLog");
			while(rs.next()){
				System.out.println(rs.getDate("startTime") + " \t| " 
								+ rs.getDate("endTime") + " \t| "
								+ rs.getInt("surveyNum") + " \t\t\t| "
								+ rs.getInt("frameNum") + " \t\t| "
								+ rs.getInt("numberOfEmails"));
			}
		} catch (Exception e){
			throw e;
		} finally{
			close();
		}
		
	}
	
	/*
	 * Reads the CSV at fn and collects every instance into a Vector<String>
	 */
	private Vector<String> getColumn(String fn, int columnNum, int skipRows) throws Exception{
		String strLine;
		Vector<String> records = new Vector<String>();
		
		// Read the file, line by line
		BufferedReader br = new BufferedReader(new FileReader(fn));
		while (skipRows-- != 0) br.readLine();
		while ((strLine = br.readLine()) != null){			
			// Read the line, comma separated token by comma separated token
			records.add(strLine.split(",")[columnNum]);
		}
		
		return records;
	}
	
	/*
	 * Create the DB connection
	 */
	private void open() throws Exception{
		// Load driver
		Class.forName("com.mysql.jdbc.Driver");
		connect = DriverManager.getConnection("jdbc:mysql://localhost/graduate?user=graduate&password=h0mework");
		statement = connect.createStatement();
	}
	
	/*
	 * Closes various database objects
	 */
	private void close() throws Exception {
		if (rs != null) {
			rs.close();
		}

		if (statement != null) {
			statement.close();
		}

		if (connect != null) {
			connect.close();
		}
	}
	
	/*
	 * Create the email and send it to the specified survey frame
	 * This function contains the user's username and password
	 */
	private void sendEmails(int surveyNum, int frameNum) throws Exception{
		int numberOfEmails = 0;
		DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		String participantID;
		String emailedUrl;
		Properties props = new Properties();
		int emailRounds = 0;
		
		System.out.println ("Emailing frame number " + frameNum + " of survey number " + surveyNum + ".");
		// Note the start time
		java.util.Date startDate = new Date();
		
		// Get the list of email addresses
		try{
			open();
			
			// First make sure I haven't already sent emails 3 times
			rs = statement.executeQuery("select count(*) from emailLog where surveyNum = " + surveyNum + " and frameNum = " + frameNum);
			if (rs.next()){
				emailRounds = rs.getInt(1);
				if (emailRounds >= 3){
					System.out.println("You have already email this survey frame 3 times. You can not email it anymore.");
					return;
				};
			} else {
				throw new Exception("Something went really wrong retrieving the number of previous email attempts from the database.");
			}
			
			// Setup the emailer
			props.put("mail.smtp.host", "smtp.umn.edu");
			props.put("mail.from", "bush0287@umn.edu");
			props.put("mail.smtp.starttls.enable", "true");
			Session session = Session.getInstance(props, null);

			
			// Get the information we need from the database
			rs = statement.executeQuery("select email, participantID from email where surveyNum = " + surveyNum 
									+ " and frameNum = " + frameNum + " and hasResponded = 0");
			while (rs.next()){
				numberOfEmails++;
				System.out.print("Sending Email " + numberOfEmails + " ... ");
				participantID = rs.getString(2); // participantID
				emailedUrl = surveyAddresses[(surveyNum)] + "?c=" + participantID;  
				MimeMessage msg = getEmail(emailedUrl, rs.getString(1), session, emailRounds);
				
				// Send the Email!
				Transport tr = session.getTransport("smtp");
				tr.connect("smtp.umn.edu", "bush0287", "<password>");
				tr.sendMessage(msg, msg.getAllRecipients());
				tr.close();
				
				System.out.println("Email Sent");
			}
		} catch (Exception e){
			throw e;
		} finally {
			close();
		}
		
		// Magic HERE!!!!!1
		 
		// Note the end time
		java.util.Date endDate = new Date();
		 
		try {
			open();
			
			// Update the log
			preparedStatement = connect.prepareStatement("insert into emailLog(surveyNum, frameNum, startTime, endTime, numberOfEmails) values (?,?,?,?,?)");
			preparedStatement.setInt(1, surveyNum);
			preparedStatement.setInt(2, frameNum);
			preparedStatement.setTimestamp(3, new java.sql.Timestamp(startDate.getTime()));
			preparedStatement.setTimestamp(4, new java.sql.Timestamp(endDate.getTime()));
			preparedStatement.setInt(5, numberOfEmails);
			preparedStatement.execute();
		} catch (Exception e){
			throw e;
		} finally {
			close();
		}
	}
	
	/*
	 * Builds the email message as HTML, including the provided url, and returns it.
	 */
	private MimeMessage getEmail(String url, String toAddress, Session session, int emailRounds) throws MessagingException, IOException{
		MimeMessage msg = new MimeMessage(session);
		msg.setFrom();
		msg.setRecipients(Message.RecipientType.TO, toAddress);
		msg.setSubject("A Survey From a Fellow Student And A Chance To Win 25 Bucks");
		msg.setSentDate(new Date());
		msg.setHeader("X-Mailer", "sendhtml");
		
		// Build an HTML version of the email
		StringBuffer sb = new StringBuffer();
		sb.append("<HTML>\n");
		sb.append("<HEAD>\n");
		sb.append("<TITLE>\n");
		sb.append(msg.getSubject() + "\n");
		sb.append("</TITLE>\n");
		sb.append("</HEAD>\n");
		sb.append("<BODY>\n");
		
		if (emailRounds < 1){
			// Build the body of the email
			sb.append("<H3>" + msg.getSubject() + "</H3>" + "\n");
			// First paragraph
			sb.append("<p>Hello, I am a master’s student here at the University of Minnesota, and I am really interested in helping students deal with the issue of online privacy. Because of that interest (and because I want to graduate), I am conducting an online survey of University of Minnesota students about their feelings on privacy, especially the trust they place in certain unknown organizations to help them understand the privacy policies of websites. I am sending you this email because I hope you will take 5 to 10 minutes to take the survey found at ");
			sb.append("<a href=\"" + url + "\">" + url + "</a>"); 
			sb.append(".</p>\n");
			// Second paragraph
			sb.append( "<p>You will not be asked to share any personal information during the survey. This survey will only ask you about how much trust you place in an organization to help you with online privacy, how much you would be willing to pay that organization to help you, how concerned you are about privacy, and some basic demographic information. You are not required to take this survey for any reason, and you are not required to finish the survey once you start it.</p>\n");
			// Third paragraph
			sb.append("<p>That said, I hope you will take the short time to start and finish the survey. The data I hope to collect from students who take this survey will help me to develop better ways for policy makers, businesses, and computer professionals to address the growing issue of online privacy. At the end of the survey you will be presented with information and links to tools that help you to protect your privacy on the Internet. Also, if you submit a finished survey, you will be entered into a random drawing for a $25 gift card to Amazon.com, which will take place on May 31st.</p>\n" );
			// Fourth paragraph
			sb.append("<p>To start the survey, please go to ");
			sb.append("<a href=\"" + url + "\">" + url + "</a>");
			sb.append("</p>\n\n");
			// Closing
			sb.append("<p>Thank You,</p>\n");
			sb.append("<p>Bill Bushey</p>");
		} else if (emailRounds == 1) {
			// Build the body of the email
			sb.append("<H3>" + msg.getSubject() + "</H3>" + "\n");
			// First paragraph
			sb.append("<p>Hello, this is a friendly reminder of the trust in online privacy survey found at ");
			sb.append("<a href=\"" + url + "\">" + url + "</a>. "); 
			sb.append("I encourage you to take 5 to 10 minutes to fill out this survey, as your response will help me to develop government and business policies that will protect user privacy on the Internet. You will receive one final reminder about this survey at the end of this week.");
			sb.append("</p>\n");
			// Second paragraph
			sb.append( "<p>You will not be asked to share any personal information during the survey. This survey will only ask you about how much trust you place in an organization to help you with online privacy, how much you would be willing to pay that organization to help you, how concerned you are about privacy, and some basic demographic information. You are not required to take this survey for any reason, and you are not required to finish the survey once you start it.</p>\n");
			// Third paragraph
			sb.append("<p>That said, I hope you will take the short time to start and finish the survey. At the end of the survey you will be presented with information and links to tools that help you to protect your privacy on the Internet. Also, if you submit a finished survey, you will be entered into a random drawing for a $25 gift card to Amazon.com, which will take place on May 31st.</p>\n" );
			// Fourth paragraph
			sb.append("<p>To start the survey, please go to ");
			sb.append("<a href=\"" + url + "\">" + url + "</a>");
			sb.append("</p>\n\n");
			// Closing
			sb.append("<p>Thank You,</p>\n");
			sb.append("<p>Bill Bushey</p>");
		} else {
			// Build the body of the email
			sb.append("<H3>" + msg.getSubject() + "</H3>" + "\n");
			// First paragraph
			sb.append("<p>Hello, this is a friendly reminder of the trust in online privacy survey found at ");
			sb.append("<a href=\"" + url + "\">" + url + "</a>. "); 
			sb.append("I encourage you to take 5 to 10 minutes to fill out this survey, as your response will help me to develop government and business policies that will protect user privacy on the Internet. This is the final email you will receive about this survey.");
			sb.append("</p>\n");
			// Second paragraph
			sb.append( "<p>You will not be asked to share any personal information during the survey. This survey will only ask you about how much trust you place in an organization to help you with online privacy, how much you would be willing to pay that organization to help you, how concerned you are about privacy, and some basic demographic information. You are not required to take this survey for any reason, and you are not required to finish the survey once you start it.</p>\n");
			// Third paragraph
			sb.append("<p>That said, I hope you will take the short time to start and finish the survey. At the end of the survey you will be presented with information and links to tools that help you to protect your privacy on the Internet. Also, if you submit a finished survey, you will be entered into a random drawing for a $25 gift card to Amazon.com, which will take place on May 31st.</p>\n" );
			// Fourth paragraph
			sb.append("<p>To start the survey, please go to ");
			sb.append("<a href=\"" + url + "\">" + url + "</a>");
			sb.append("</p>\n\n");
			// Closing
			sb.append("<p>Thank You,</p>\n");
			sb.append("<p>Bill Bushey</p>");
		}
		
		// Close HTML
		sb.append("</BODY>\n");
		sb.append("</HTML>\n");

		msg.setDataHandler(new DataHandler(new ByteArrayDataSource(sb.toString(), "text/html")));

		msg.saveChanges();
		return msg;
	}
}
