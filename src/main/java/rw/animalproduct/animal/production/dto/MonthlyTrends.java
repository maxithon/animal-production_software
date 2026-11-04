package rw.animalproduct.animal.production.dto;

public class MonthlyTrends {
    private int[] monthlyBirths       = new int[12];
    private int[] monthlySales        = new int[12];
    private int[] monthlyDeaths       = new int[12];
    private int[] monthlyTreatments   = new int[12];
    private int[] monthlySick         = new int[12];   // NEW — real sick events per month
    private int[] monthlyAbortions    = new int[12];   // NEW — real abortions per month
    private int[] monthlyPregnancies  = new int[12];   // NEW — confirmed pregnancies per month
    private double[] monthlyRevenue   = new double[12];
    private double[] monthlyCosts     = new double[12];
    private double[] monthlyDeathValue= new double[12];

    public int[] getMonthlyBirths() { return monthlyBirths; }
    public void setMonthlyBirths(int[] v) { this.monthlyBirths = v; }

    public int[] getMonthlySales() { return monthlySales; }
    public void setMonthlySales(int[] v) { this.monthlySales = v; }

    public int[] getMonthlyDeaths() { return monthlyDeaths; }
    public void setMonthlyDeaths(int[] v) { this.monthlyDeaths = v; }

    public int[] getMonthlyTreatments() { return monthlyTreatments; }
    public void setMonthlyTreatments(int[] v) { this.monthlyTreatments = v; }

    public int[] getMonthlySick() { return monthlySick; }
    public void setMonthlySick(int[] v) { this.monthlySick = v; }

    public int[] getMonthlyAbortions() { return monthlyAbortions; }
    public void setMonthlyAbortions(int[] v) { this.monthlyAbortions = v; }

    public int[] getMonthlyPregnancies() { return monthlyPregnancies; }
    public void setMonthlyPregnancies(int[] v) { this.monthlyPregnancies = v; }

    public double[] getMonthlyRevenue() { return monthlyRevenue; }
    public void setMonthlyRevenue(double[] v) { this.monthlyRevenue = v; }

    public double[] getMonthlyCosts() { return monthlyCosts; }
    public void setMonthlyCosts(double[] v) { this.monthlyCosts = v; }

    public double[] getMonthlyDeathValue() { return monthlyDeathValue; }
    public void setMonthlyDeathValue(double[] v) { this.monthlyDeathValue = v; }
}