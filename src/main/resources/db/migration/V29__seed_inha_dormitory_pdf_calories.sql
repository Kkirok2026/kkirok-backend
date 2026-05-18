update cafeteria_menu_option
set calories_kcal = case option_id
    when 2021 then 918.00
    when 2022 then 978.00
    when 2023 then 1013.00
    when 2024 then 919.00
    when 2025 then 1021.00
    when 2026 then 881.00
    when 2027 then 956.00
    when 2028 then 888.00
    when 2029 then 669.00
    when 2030 then 673.00
    when 2031 then 455.00
    when 2032 then 628.00
    when 2033 then 741.00
    when 2034 then 0.00
    when 2035 then 334.00
    when 2036 then 995.00
    when 2037 then 0.00
    when 2038 then 608.00
    when 2039 then 1081.00
    when 2040 then 0.00
    when 2041 then 268.00
    when 2042 then 828.00
    when 2043 then 0.00
    when 2044 then 407.00
    when 2045 then 1169.00
    when 2046 then 0.00
    when 2047 then 513.00
    when 2048 then 870.00
    when 2049 then 487.00
    else calories_kcal
end
where option_id between 2021 and 2049;
