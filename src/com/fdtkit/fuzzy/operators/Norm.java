/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package src.com.fdtkit.fuzzy.operators;

/**
 *
 * @author Mohammed H. Jabreel
 */
public interface Norm {
    
    double evaluate( double membershipA, double membershipB );
}
