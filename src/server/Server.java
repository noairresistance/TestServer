package server;


import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.PriorityQueue;
import java.util.Scanner;
import Food.*;
import java.util.LinkedList;
import java.util.Queue;

public class Server
{
    private ServerSocket ServerSkt = null; // the server socket
    private ObjectOutputStream[] TableOutObjs; // an array of Object output streams for tables
    private ObjectOutputStream[] WaiterOutObjs; // an array of Object output stream for waiters
    private ObjectOutputStream KitchenOutObj = null; // an object output stream for the kitchen
    private ObjectInputStream[] TableInObjs; // an array of object input streams for tables
    private ObjectInputStream[] WaiterInObjs; // an array of object input streams for waiters
    private ObjectInputStream KitchenInObj = null; // an object input stream for the kitchen
   
    public ServerSentMasterList SentMenu = null; 
    private MasterFoodItemList Menu = null; 
    private int WaiterCount = 0; // a counter to keep track of the waiters connected to the server
    private Queue<Integer> Waiters; // a queue of ints to assign waiters to a table
    private boolean shutdown = false; // a boolean to represent if the server is shutdown
       
    public Server()
    {
        Waiters = new LinkedList<Integer>(); // create a queue of integers
    }
    
    // this is the thread that accepts connections 
    public class Handshake implements Runnable 
    {       
        @Override
        public void run()
        {
            System.out.println("insided handshake thread.");
            
            TableOutObjs = new ObjectOutputStream[16]; // create an array of object output streams to communicate with the tables
            WaiterOutObjs = new ObjectOutputStream[2]; // create an array of object output streams to communicate with the waiters
            TableInObjs = new ObjectInputStream[16]; // create an array of object input streams to receive messages from the tables
            WaiterInObjs = new ObjectInputStream[2]; // create an array of object input streams to recieve messages from the waiters
            
            try
            {
                ServerSkt = new ServerSocket(5555); // set the server to listen to connection on port 5555
                
                while(!shutdown)
                {
                    Socket newConnection = ServerSkt.accept(); // accept a new client
                    String[] MessageTokens; // a string array used to parse a string if necessary
                    
                    // create object input and ouput streams for the new user
                    ObjectInputStream clientObjIn;
                    ObjectOutputStream clientObjOut;
                    
                    clientObjOut = new ObjectOutputStream(newConnection.getOutputStream());
                    clientObjOut.flush();
                    clientObjIn = new ObjectInputStream(newConnection.getInputStream());
                    
                    String InitMessage = clientObjIn.readUTF(); // read the message
                    
                    if(InitMessage.startsWith("Table")) // if the new connection is a table
                    {
                        // put the input stream in the array and launch customer thread 
                        MessageTokens = InitMessage.split("@");
                        int i = Integer.parseInt(MessageTokens[1]);
                        TableOutObjs[i-1] = clientObjOut; // put the output stream in the array
                        TableInObjs[i-1] = clientObjIn; // put the input stream in the array
                        
                        // create handler thread
                        Thread Table = new Thread(new TableThread(i, clientObjOut, clientObjIn, newConnection));
                        Table.start();
                    }
                    else if(InitMessage.equals("Waiter")) // if the new connection is a waiter
                    {
                        // check if the server can accept more waiters
                        if(WaiterCount > 2)
                        { 
                            System.out.println("Cannot accept more waiters.");
                            clientObjOut.close(); // reject the connection
                            clientObjIn.close(); // reject the connection
                        }
                        else // accept the connection
                        {
                            WaiterOutObjs[WaiterCount] = clientObjOut; // add the output stream to the array
                            WaiterInObjs[WaiterCount] = clientObjIn; // add the input stream to the array
                            Waiters.add(WaiterCount); // place the waiter's number in the queue
                            
                            //launch waiter thread
                            Thread Waiter = new Thread(new WaiterThread(WaiterCount,clientObjIn, clientObjOut, newConnection));
                            Waiter.start();
                            
                            WaiterCount++; // increment the counter
                        }
                    }
                    else if(InitMessage.equals("Kitchen")) // if the connection is the kitchen
                    {
                        KitchenOutObj = clientObjOut; // assign the outuput
                        KitchenInObj = clientObjIn; // assign the input
                        
                        // launch a thread
                        Thread Kitchen = new Thread(new KitchenThread(clientObjIn, clientObjOut, newConnection));
                        Kitchen.start();
                    }
                    else // could not determine the type of connection
                    {
                        System.out.println("Could not determine the type of connection");
                    }
                }
                
                // close the server socket
                ServerSkt.close();
            }
            catch(Exception e)
            {
                System.out.println("Error connecting to server." + e);
            }
        }
    }
    
    // a function that launches the handshake function
    public void launch()
    {
        System.out.println("Starting thread.");
        Thread connect = new Thread(new Handshake());
        connect.start();
    }
    
    // this class is the thread responsible for handling communications by a table
    public class TableThread implements Runnable
    {
        int TableNumber; // the table's number
        ObjectOutputStream ObjOut; // the output stream
        ObjectInputStream ObjIn; // the input stream 
        int AssignedWaiter; // the assigned waiter
        Socket TableSkt = null; // the table's socket
        Order Order; // and order belonging to the table, holds restaurant items and drinks
                
        public TableThread(int num, ObjectOutputStream out, ObjectInputStream in, Socket Skt)
        {
            // initialize the thread variables
            AssignedWaiter = -1; // when the thread is created the table has no waiter assigned
            TableNumber = num;
            ObjOut = out;
            ObjIn = in;
            TableSkt = Skt;
        }
        
        @Override
        public void run()
        {
            String Request; // the Table's request
            
            try
            {
                // send the menu to the table before accepting requests
                ObjOut.writeObject(SentMenu);
                ObjOut.flush();
                
                // accept requests from the table
                while((Request = ObjIn.readUTF()) != null)
                {
                    System.out.println(Request); // test
                    
                    // assign waiter if the table does not have one already
                    if(AssignedWaiter == -1)
                    {
                        // pop number from queue, assign to table, and push back into the queue
                        AssignedWaiter = Waiters.remove();
                        Waiters.add(AssignedWaiter);
                    }
                    
                    if(Request.equals("Send")) // if the table is sending an order
                    {
                        // prepare the waiter to recieve the order
                        Order tempOrder = (Order)ObjIn.readObject();
                        
                        // assign the waiter to the order
                        System.out.println(AssignedWaiter);
                        tempOrder.setWaiter(AssignedWaiter);
                        
                        // send a message to the waiter
                        WaiterOutObjs[AssignedWaiter].writeUTF("Placed");
                        WaiterOutObjs[AssignedWaiter].flush();
                        
                        // send the table's order to the assigned waiter
                        WaiterOutObjs[AssignedWaiter].writeObject(tempOrder);
                        WaiterOutObjs[AssignedWaiter].flush();
                        
                        // send a message to the kitchen
                        /*KitchenOutObj.writeUTF("Placed");
                        KitchenOutObj.flush();
                        
                        // send the table's order to the kitchen
                        KitchenOutObj.writeObject(tempOrder);
                        KitchenOutObj.flush();*/
                        
                    }
                    else if(Request.equals("Help")) // if the table requested help
                    {
                        // read the message and send the message to the waiter
                        String help = ObjIn.readUTF();
                        WaiterOutObjs[AssignedWaiter].writeUTF("Help");
                        WaiterOutObjs[AssignedWaiter].flush();
                        
                        // send the request to the waiter
                        WaiterOutObjs[AssignedWaiter].writeUTF(help);
                        WaiterOutObjs[AssignedWaiter].flush();
                    }
                    else if(Request.equals("Refill")) // if the table requested a refill
                    {
                        // read the message and send the message to the waiter
                        String refill = ObjIn.readUTF();
                        WaiterOutObjs[AssignedWaiter].writeUTF("Refill");
                        WaiterOutObjs[AssignedWaiter].flush();
                        
                        // send the refill request to the waiter
                        WaiterOutObjs[AssignedWaiter].writeUTF(refill);
                        WaiterOutObjs[AssignedWaiter].flush();
                    }
                    else if(Request.equals("Cash")) // if the table paid by cash
                    {
                        // read the message and send the message to the waiter
                        String cash = ObjIn.readUTF();
                        WaiterOutObjs[AssignedWaiter].writeUTF("Cash");
                        WaiterOutObjs[AssignedWaiter].flush();
                        
                        // send the message to waiter
                        WaiterOutObjs[AssignedWaiter].writeUTF(cash);
                        WaiterOutObjs[AssignedWaiter].flush();
                    }
                    
                    else if(Request.equals("Card")) // if the table paid by card
                    {
                        // read the message and send the message to the waiter
                        String card = ObjIn.readUTF();
                        WaiterOutObjs[AssignedWaiter].writeUTF("Card");
                        WaiterOutObjs[AssignedWaiter].flush();
                        
                        // send the message to the waiter
                        WaiterOutObjs[AssignedWaiter].writeUTF(card);
                        WaiterOutObjs[AssignedWaiter].flush();
                    }
                    
                    else if(Request.equals("togo"))
                    {
                        // read the message and send the message to the waiter
                        String togo = ObjIn.readUTF();
                        WaiterOutObjs[AssignedWaiter].writeUTF("togo");
                        WaiterOutObjs[AssignedWaiter].flush();
                        
                        // send the message to the waiter
                        WaiterOutObjs[AssignedWaiter].writeUTF(togo);
                        WaiterOutObjs[AssignedWaiter].flush();
                    }
                    else if(Request.equals("Free"))
                    {
                        // read the message and send the message to the waiter
                        String free = ObjIn.readUTF();
                        WaiterOutObjs[AssignedWaiter].writeUTF("Free");
                        WaiterOutObjs[AssignedWaiter].flush();
                        
                        // send the message to the waiter
                        WaiterOutObjs[AssignedWaiter].writeUTF(free);
                        WaiterOutObjs[AssignedWaiter].flush();
                    }
                }
                
                // when the connection is closed
                TableOutObjs[TableNumber] = null; // set the location in the ouput stream array to null
                TableInObjs[TableNumber] = null; // set the location in the input stream array to null
                ObjIn.close(); // close the input stream
                ObjOut.close(); // close the output stream 
                TableSkt.close(); // close the socket
            }
            catch(Exception e)
            {
                System.out.println("Error receiving request." + e);
            }
        }
    }
    
    // the thread that is responsible for listening to request from the waiter
    public class WaiterThread implements Runnable
    {
        ObjectOutputStream ObjOut = null; // the waiter's output stream
        ObjectInputStream ObjIn = null; // the waiter's input stream
        Socket WaiterSkt = null; // the waiter's socket
        int WaiterID = -1; // the waiter's id
        Order tempOrder = null; // a temporary orderlist object used when a waiter modifies a table's order
        
        public WaiterThread(int id, ObjectInputStream in, ObjectOutputStream out, Socket Skt)
        {
            // initialize the waiter's values
            WaiterID = id;
            ObjIn = in;
            ObjOut = out;
            WaiterSkt = Skt;
        }
        
        @Override
        public void run()
        {
            String Request; // a string used to receive requests from the waiter
            
            try
            {
                while((Request = ObjIn.readUTF()) != null) // while the server can read messages from the waiter
                {
                    System.out.println(Request); // test
                    if(Request.startsWith("Modify")) // string needs Modify@Table@number
                    {
                        // determine which table number needs their order modified
                        String[] RequestTokens = Request.split("@");
                        int i = (Integer.parseInt(RequestTokens[1]))-1;
                        
                        Order tempOrder = (Order)ObjIn.readObject(); // get the modified order from the waiter
                        
                        // send a message to prepare the table
                        TableOutObjs[i].writeUTF(RequestTokens[0]);
                        TableOutObjs[i].flush();
                        Thread.sleep(100);
                        TableOutObjs[i].writeObject(tempOrder);
                        TableOutObjs[i].flush();
                    }
                }
                
                WaiterOutObjs[WaiterID] = null; // set the output location to null
                WaiterInObjs[WaiterID] = null; // set the input location to null
                ObjIn.close(); // close the input stream
                ObjOut.close(); // close the outputstream
                WaiterSkt.close(); // close the socket
            }
            catch(Exception e)
            {
                System.out.println("Could not receive request from waiter."+e);
            }
        }
    }
    
    // this is the thread class that is responsible for listening for messages from the kitchen
    public class KitchenThread implements Runnable
    {
        ObjectInputStream ObjIn; // the kitchen's input stream
        ObjectOutputStream ObjOut; // the kitchen's output stream 
        Socket KitchenSkt; // the kitchen's socket
        
        public KitchenThread(ObjectInputStream in, ObjectOutputStream out, Socket skt)
        {
            // assign the kitchen's valuess
            ObjIn = in;
            ObjOut = out;
            KitchenSkt = skt;
        }
        
        public void run()
        {
            String Request; // a string used to receive messages from the kitchen
            
            try
            {
                while((Request = ObjIn.readUTF()) != null) // while the server can receive messages from the kitchen
                {                    
                    if(Request.equals("Waiter")) // if the kitchen requests a waiter
                    {
                        // determine which waiter to talk to
                        int waiter = ObjIn.readInt();
                        String message = ObjIn.readUTF();
                        
                        // send the message to the waiter
                        WaiterOutObjs[waiter].writeUTF("Waiter");
                        WaiterOutObjs[waiter].flush();
                        
                        WaiterOutObjs[waiter].writeUTF(message);
                        WaiterOutObjs[waiter].flush();
                    }
                    else if(Request.equals("Ready")) // if the kitchen completed an order
                    {
                        // detemine which waiter to send the notification to
                        int waiter = ObjIn.readInt();
                        String message = ObjIn.readUTF();
                        
                        // send the message to the waiter
                        WaiterOutObjs[waiter].writeUTF("Ready");
                        WaiterOutObjs[waiter].flush();
                        
                        WaiterOutObjs[waiter].writeUTF(message);
                        WaiterOutObjs[waiter].flush();
                    }
                }
            }
            catch(Exception e)
            {
                System.out.println("Error connection to the kitchen." + e);
            }
        }
    }
    
    // this function is used to build the menu 
    public void buildMenu()
    {
         DrinksList drinks = new DrinksList(); // create the drinks
         AppitizersList apps = new AppitizersList(); // create the appetizers
         EntreeList Entrees = new EntreeList(); // create the entrees
         DessertsList Desserts = new DessertsList(); // create the desserts
         MerchandiseList Merchandise = new MerchandiseList(); // create the mechandise
         SpecialsList Specials = new SpecialsList(); // create the specials
         
         //building specials 
         for (int i = 0; i < 2; i++)
         {
             if(drinks.drinks.get(i).GetIsSpecial() == Boolean.TRUE)
             {
                 Specials.addItem(drinks.drinks.get(i));
             }
             if(apps.appitizers.get(i).GetIsSpecial() == Boolean.TRUE)
             {
                 Specials.addItem(apps.appitizers.get(i));
             }
             if(Entrees.entrees.get(i).GetIsSpecial() == Boolean.TRUE)
             {
                 Specials.addItem(Entrees.entrees.get(i));
             }
             if(Desserts.desserts.get(i).GetIsSpecial() == Boolean.TRUE)
             {
                 Specials.addItem(Desserts.desserts.get(i));
             }
         }
         
         SentMenu = new ServerSentMasterList(drinks.drinks, apps.appitizers, Entrees.entrees, Desserts.desserts, Merchandise.merchandise, Specials.specials);
         //Menu = new MasterFoodItemList(SentMenu.totalList);
         
         //This was used to test that the correct items were added to the Special's List.
         /*
         System.out.println("ListSize " + Specials.getListSize());
         System.out.println(Specials.getItem(0).GetName());
         System.out.println(Specials.getItem(1).GetName());
         System.out.println(Specials.getItem(2).GetName());
         System.out.println(Specials.getItem(3).GetName());
         */
    }
    
    public static void main(String argv[])
    {
        // create a new order
        Server server = new Server();
        server.buildMenu();
        
        System.out.println("starting server.");
        server.launch(); // launch the server
    }
}
