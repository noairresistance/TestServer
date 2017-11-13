package server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.PriorityQueue;
import java.util.Scanner;

import allclasses.*;
import java.util.LinkedList;
import java.util.Queue;

public class Server
{
    private ServerSocket ServerSkt = null;
    private ObjectOutputStream[] TableOutObjs;
    private ObjectOutputStream[] WaiterOutObjs;
    private ObjectOutputStream KitchenOutObj = null;
    private ObjectInputStream[] TableInObjs;
    private ObjectInputStream[] WaiterInObjs;
    private ObjectInputStream KitchenInObj = null;
   
    private FullMenu menu = null;
    private int WaiterCount = 0;
    private Queue<Integer> Waiters;
    private boolean shutdown = false;
       
    public Server()
    {
        Waiters = new LinkedList<Integer>();
    }
    
    // this is the thread that accepts connections 
    public class Handshake implements Runnable 
    {       
        @Override
        public void run()
        {
            System.out.println("insided handshake thread.");
            
            TableOutObjs = new ObjectOutputStream[16];
            WaiterOutObjs = new ObjectOutputStream[2];
            TableInObjs = new ObjectInputStream[16];
            WaiterInObjs = new ObjectInputStream[2];
            
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
                                        
                    System.out.println("receiving message."); // test
                    String InitMessage = clientObjIn.readUTF(); // wait for message
                    System.out.println("got message: " + InitMessage); // test
                    
                    if(InitMessage.startsWith("Table"))
                    {
                        // put the input stream in the array and launch customer thread make sure to pass the stream
                        
                        MessageTokens = InitMessage.split("@");
                        int i = Integer.parseInt(MessageTokens[1]);
                        TableOutObjs[i-1] = clientObjOut;
                        TableInObjs[i-1] = clientObjIn;
                        
                        // create handler thread
                        Thread Table = new Thread(new TableThread(i, clientObjOut, clientObjIn, newConnection));
                        Table.start();
                    }
                    else if(InitMessage.equals("Waiter"))
                    {
                        if(WaiterCount > 2) // should change to some global
                        { 
                            System.out.println("Cannot accept more waiters.");
                        }
                        else
                        {
                                                    System.out.println("test1");
                            WaiterOutObjs[WaiterCount] = clientObjOut;
                            System.out.println("test2");
                            WaiterInObjs[WaiterCount] = clientObjIn;
                            System.out.println("test3");
                            Waiters.add(WaiterCount);
                            System.out.println("test4");
                            
                            //launch waiter thread
                            Thread Waiter = new Thread(new WaiterThread(WaiterCount,clientObjIn, clientObjOut, newConnection));
                            Waiter.start();
                            WaiterCount++;
                        }
                    }
                    else if(InitMessage.equals("Kitchen"))
                    {
                        KitchenOutObj = clientObjOut;
                        KitchenInObj = clientObjIn;
                        
                        Thread Kitchen = new Thread(new KitchenThread(clientObjIn, clientObjOut, newConnection));
                        Kitchen.start();
                    }
                    else
                    {
                        System.out.println("Could not determine the type of connection");
                    }
                }
                
                // need to ensure that all connect users have no data before closing
                ServerSkt.close();
            }
            catch(Exception e)
            {
                System.out.println("Error connecting to server." + e);
            }
        }
    }
    
    // function used for testing, it launches the handshake thread
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
                System.out.println("Sending menu");
                ObjOut.writeObject(menu);
                ObjOut.flush();
                
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
                    
                    if(Request.equals("Send"))
                    {
                        System.out.println("getting order"); // test
                        
                        // prepare the waiter to recieve the order
                        Order tempOrder = (Order)ObjIn.readObject();
                        WaiterOutObjs[AssignedWaiter].writeUTF("Placed");
                        WaiterOutObjs[AssignedWaiter].flush();
                        
                        // read an orderlist object from the table
                        WaiterOutObjs[AssignedWaiter].writeObject(tempOrder);
                        WaiterOutObjs[AssignedWaiter].flush();
                        
                        KitchenOutObj.writeUTF("Placed");
                        KitchenOutObj.flush();
                        
                        KitchenOutObj.writeObject(tempOrder);
                        KitchenOutObj.flush();
                    }
                    else if(Request.startsWith("Help"))
                    {
                        WaiterOutObjs[AssignedWaiter].writeUTF(Request);
                        WaiterOutObjs[AssignedWaiter].flush();
                        //ObjOut.writeUTF("Shutdown"); // test
                        //ObjOut.flush(); //test
                        //break; // test
                    }
                    else if(Request.equals("Refill"))
                    {
                        // read an arraylist of drinks
                        // relay the lis to the waiter
                    }
                    else if(Request.startsWith("Paid"))
                    {
                        WaiterOutObjs[AssignedWaiter].writeUTF(Request);
                        WaiterOutObjs[AssignedWaiter].flush();
                    }
                }
                TableOutObjs[TableNumber] = null;
                TableInObjs[TableNumber] = null;
                ObjIn.close();
                ObjOut.close();
                TableSkt.close();
                
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
        ObjectOutputStream ObjOut = null;
        ObjectInputStream ObjIn = null;
        Socket WaiterSkt = null;
        int WaiterID = -1;
        Order tempOrder = null; // a temporary orderlist object used when a waiter modifies a table's order
        
        public WaiterThread(int id, ObjectInputStream in, ObjectOutputStream out, Socket Skt)
        {
            WaiterID = id;
            ObjIn = in;
            ObjOut = out;
            WaiterSkt = Skt;
        }
        
        @Override
        public void run()
        {
            String Request;
            
            try
            {
                while((Request = ObjIn.readUTF()) != null)
                {
                    System.out.println(Request); // test
                    if(Request.startsWith("Modify")) // string needs Modify@Table@number
                    {
                        // determine which table number needs their order modified
                        String[] RequestTokens = Request.split("@");
                        int i = (Integer.parseInt(RequestTokens[1]))-1;
                        
                        Order tempOrder = (Order)ObjIn.readObject();
                        
                        // send a message to prepare the 
                        TableOutObjs[i].writeUTF(RequestTokens[0]);
                        TableOutObjs[i].flush();
                        Thread.sleep(100);
                        TableOutObjs[i].writeObject(tempOrder);
                        TableOutObjs[i].flush();
                       
                        // test code
                        //ObjOut.writeUTF("Shutdown");
                        //ObjOut.flush();
                        //break;
                    }
                    else if(Request.equals("Dismiss"))
                    {
                        // 
                    }
                }
                
                WaiterOutObjs[WaiterID] = null;
                WaiterInObjs[WaiterID] = null;
                ObjIn.close();
                ObjOut.close();
                WaiterSkt.close();
            }
            catch(Exception e)
            {
                System.out.println("Could not receive request from waiter."+e);
            }
        }
    }
    
    public class KitchenThread implements Runnable
    {
        ObjectInputStream ObjIn;
        ObjectOutputStream ObjOut;
        Socket KitchenSkt;
        
        public KitchenThread(ObjectInputStream in, ObjectOutputStream out, Socket skt)
        {
            ObjIn = in;
            ObjOut = out;
            KitchenSkt = skt;
        }
        
        
        public void run()
        {
            String Request;
            
            try
            {
                while((Request = ObjIn.readUTF()) != null)
                {
                    // if the kitchen requests a waiter
                    if(Request.equals("Request"))
                    {
                        
                    }
                    // else if the an order is done
                    else if(Request.equals("Done"))
                    {
                        
                    }
                }
            }
            catch(Exception e)
            {
                System.out.println("Error connection to the kitchen." + e);
            }
        }
    }
    
    public FoodList buildDrinks()
    {
        FoodList tempDrinks = new FoodList();
        
        Food drink1 = new Food("Coke", "Drink",1.99, Boolean.TRUE, Boolean.FALSE);
        Food drink2 = new Food("Sprite", "Drink",2.99, Boolean.TRUE, Boolean.FALSE);
        Food drink3 = new Food("Pepsi", "Drink",3.99, Boolean.TRUE, Boolean.FALSE);
        
        tempDrinks.addItem(drink1);
        tempDrinks.addItem(drink2);
        tempDrinks.addItem(drink3);
        
        return tempDrinks;
    }
    
    public FoodList buildApps()
    {
        FoodList tempApps = new FoodList();
        
        Food appitizer1 = new Food("Buffalo Wings", "Food",5.99, Boolean.TRUE, Boolean.FALSE);
        appitizer1.SetDescription("Some pretty good buffalo wings\n");
        
        Food appitizer2 = new Food("Fried Pickles", "Food",6.99, Boolean.TRUE, Boolean.FALSE);
        appitizer2.SetDescription("I mean, cmon.\n");
        
        Food appitizer3 = new Food("French fries", "Food",7.99, Boolean.TRUE, Boolean.FALSE);
        appitizer3.SetDescription("I have no clue\n");
        
        tempApps.addItem(appitizer1);
        tempApps.addItem(appitizer2);
        tempApps.addItem(appitizer3);
        
        return tempApps;
    }
    
    public FoodList buildEntrees()
    {
        FoodList tempEntrees = new FoodList();
        
        Food entree1 = new Food("Burger1", "Food",9.99, Boolean.TRUE, Boolean.FALSE);
        entree1.SetDescription("Heart-Stopping, All-American Cheese Burger with an extra large side of Freedom");
        entree1.SetIngredients("1/2 LB Patty");
        entree1.SetIngredients("Cheddar Cheese");
        entree1.SetIngredients("Lettuce");
        entree1.SetIngredients("Pickles");
        entree1.SetIngredients("Onion");
        entree1.SetIngredients("Mayo");
        
        Food entree2 = new Food("Burger2", "Food",10.99, Boolean.TRUE, Boolean.FALSE);
        entree2.SetDescription("Bla bla blablablablablablablablala abala ablabab abl");
        entree2.SetIngredients("Bla bla bla");
        entree2.SetIngredients("Ndikin");
        entree2.SetIngredients("Jeignbkd");
        entree2.SetIngredients("Goenfksl");
        entree2.SetIngredients("Nei");
        entree2.SetIngredients("Mkenbd");
        
        Food entree3 = new Food("Burger3", "Food",12.99, Boolean.TRUE, Boolean.FALSE);
        entree3.SetDescription("Nfkdh dksoih rxkcn dofb skfn voish dkdn foksn dlks ksn fkn kfn dokhl skdhlkj");
        entree3.SetIngredients("Udncosn");
        entree3.SetIngredients("Kdiojh ");
        entree3.SetIngredients("Ueijddl");
        entree3.SetIngredients("Pondks");
        entree3.SetIngredients("Mikuebnd");
        
        tempEntrees.addItem(entree1);
        tempEntrees.addItem(entree2);
        tempEntrees.addItem(entree3);
        
        return tempEntrees;
    }
    
    public FoodList buildDesserts()
    {
        FoodList tempDesserts = new FoodList();
        
        Food dessert1 = new Food("Cake", "Food",7.99, Boolean.TRUE, Boolean.FALSE);
        Food dessert2 = new Food("Ice Cream", "Food",8.99, Boolean.TRUE, Boolean.FALSE);
        Food dessert3 = new Food("Cake and Ice Cream", "Food",9.99, Boolean.TRUE, Boolean.FALSE);
        
        tempDesserts.addItem(dessert1);
        tempDesserts.addItem(dessert2);
        tempDesserts.addItem(dessert3);
        
        return tempDesserts;
    }
    
    public void buildMenu()
    {
        FoodList Drinks = buildDrinks();
        FoodList Entrees = buildEntrees();
        FoodList Desserts = buildDesserts();
        FoodList Appetizers = buildApps();
        
        menu = new FullMenu(Entrees, Appetizers, Drinks, Desserts);        
    }
    
    public void printMenu()
    {
        menu.printMenu();
    }
            
    public static void main(String argv[])
    {
        Server server = new Server();
        server.buildMenu();
        server.printMenu();
        System.out.println("starting server.");
        server.launch();
    }

    
    // thread used for testing purposes
    public class test implements Runnable
    {
        public void run()
        {
            Scanner userin = new Scanner(System.in);
            int command = -1;
            
            while(command != 1)
            {
                System.out.println("Enter a command");
                command = userin.nextInt();
            }
        
        try
        {
            for(int i = 0; i < 16; i++)
            {
                if(TableOutObjs[i] != null)
                {
                    TableOutObjs[i].writeUTF("Shutdown");
                    TableOutObjs[i].flush();
                    Thread.sleep(1000);
                    TableOutObjs[i].close();
                }
            }
            
            
        }
        catch(Exception e)
        {
            System.out.println("could not shutdown");
        }
        }
    }
}
