package server;


import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.PriorityQueue;
import java.util.Scanner;
/*
import allclasses.*;
import allclasses.RestaurantItem;
import allclasses.Order;
*/

import Food.*;
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
   
    public ServerSentMasterList SentMenu = null;
    private MasterFoodItemList Menu = null;
    private int WaiterCount = 0;
//    private PriorityQueue<Integer> Waiters = null;
    private Queue<Integer> Waiters;
    private boolean shutdown = false;
       
    public Server()
    {
        Waiters = new LinkedList<Integer>();
//        Waiters = new PriorityQueue();
        // empty constructor
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
/*                    else if(InitMessage.equals("Kitchen"))
                    {
                        KitchenOutObj = clientObjOut;
                        KitchenInObj = clientObjIn;
                        
                        Thread Kitchen = new Thread(new KitchenThread(clientObjIn, clientObjOut, newConnection));
                        Kitchen.start();
                    }*/
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
                
                ObjOut.writeObject(SentMenu);
                
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
                        System.out.println("test 3"); // test
                        WaiterOutObjs[AssignedWaiter].writeUTF("Placed");
                        WaiterOutObjs[AssignedWaiter].flush();
                        
                        // read an orderlist object from the table
                        WaiterOutObjs[AssignedWaiter].writeObject(tempOrder);
                        WaiterOutObjs[AssignedWaiter].flush();
                        
                        /*KitchenOutObj.writeUTF("Placed");
                        KitchenOutObj.flush();
                        
                        KitchenOutObj.writeObject(tempOrder);
                        KitchenOutObj.flush();*/
                    }
                    //else if(Request.startsWith("Help"))
                    else if(Request.equals("Help"))
                    {
                        String help = ObjIn.readUTF();
                        WaiterOutObjs[AssignedWaiter].writeUTF("Help");
                        WaiterOutObjs[AssignedWaiter].flush();
                        
                        WaiterOutObjs[AssignedWaiter].writeUTF(help);
                        WaiterOutObjs[AssignedWaiter].flush();
                    }
                    else if(Request.equals("Refill"))
                    {
                        String refill = ObjIn.readUTF();
                        WaiterOutObjs[AssignedWaiter].writeUTF("Refill");
                        WaiterOutObjs[AssignedWaiter].flush();
                        
                        WaiterOutObjs[AssignedWaiter].writeUTF(refill);
                        WaiterOutObjs[AssignedWaiter].flush();
                    }
                    else if(Request.endsWith("cash."))
                    {
                        WaiterOutObjs[AssignedWaiter].writeUTF(Request);
                        WaiterOutObjs[AssignedWaiter].flush();
                    }
                    
                    else if(Request.endsWith("card."))
                    {
                        WaiterOutObjs[AssignedWaiter].writeUTF(Request);
                        WaiterOutObjs[AssignedWaiter].flush();
                    }
                    
                    else if(Request.endsWith("box."))
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
    
    public void buildMenu()
    {
         DrinksList drinks = new DrinksList();
         AppitizersList apps = new AppitizersList();
         EntreeList Entrees = new EntreeList();
         DessertsList Desserts = new DessertsList();
         MerchandiseList Merchandise = new MerchandiseList();
         SpecialsList Specials = new SpecialsList();
         
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
        Server server = new Server();
        server.buildMenu();
        
        
        
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
