/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Food;

import java.io.Serializable;
import java.util.ArrayList;

public class MerchList implements Serializable
{
    private ArrayList<Merch> List = null;

    public MerchList()
    {
        List = new ArrayList();
    }
    
    public void addItem(Merch newMerch)
    {
        List.add(newMerch);
    }
    
    public Merch getItem(int i)
    {
        return List.get(i);
    }
    
    public int getListSize()
    {
        return List.size();
    }
}
